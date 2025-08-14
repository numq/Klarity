#include "decoder.h"

AVPixelFormat Decoder::_getHardwareAccelerationFormat(AVCodecContext *codecContext, const AVPixelFormat *pixelFormats) {
    const AVPixelFormat *pixelFormat;

    auto selectedPixelFormat = static_cast<AVPixelFormat>(reinterpret_cast<intptr_t>(codecContext->opaque));

    for (pixelFormat = pixelFormats; *pixelFormat != AV_PIX_FMT_NONE; pixelFormat++) {
        if (*pixelFormat == selectedPixelFormat) {
            return *pixelFormat;
        }
    }

    return AV_PIX_FMT_NONE;
}

bool Decoder::_isValid() {
    return formatContext && (audioStream || videoStream);
}

bool Decoder::_hasAudio() {
    if (!audioStream || !audioCodecContext || !audioDecoder) {
        return false;
    }

    if (audioCodecContext->sample_fmt == AV_SAMPLE_FMT_NONE) {
        return false;
    }

    if (audioCodecContext->sample_rate <= 0 || audioCodecContext->ch_layout.nb_channels <= 0) {
        return false;
    }

    if (!audioFrame) {
        return false;
    }

    return true;
}

bool Decoder::_hasVideo() {
    if (!videoStream || !videoCodecContext || !videoDecoder) {
        return false;
    }

    if (!swVideoFrame) {
        return false;
    }

    return true;
}

bool Decoder::_isHardwareAccelerated() {
    if (!videoCodecContext || !videoCodecContext->hw_device_ctx || !hwVideoFrame) {
        return false;
    }

    return format.hwDeviceType != AV_HWDEVICE_TYPE_NONE;
}

bool Decoder::_prepareHardwareAcceleration(const uint32_t deviceType) {
    if (deviceType == AV_HWDEVICE_TYPE_NONE) {
        return false;
    }

    const AVCodecHWConfig *config = nullptr;

    for (int i = 0; (config = avcodec_get_hw_config(videoDecoder, i)); i++) {
        if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX && config->device_type == deviceType) {
            videoCodecContext->opaque = reinterpret_cast<void *>(static_cast<intptr_t>(config->pix_fmt));

            videoCodecContext->hw_device_ctx = HardwareAcceleration::requestContext(
                    static_cast<AVHWDeviceType>(deviceType)
            );

            hwVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

            if (!hwVideoFrame) {
                throw HardwareAccelerationException("Memory allocation failed for hardware video frame");
            }

            return true;
        }
    }

    return false;
}

int Decoder::_processAudio() {
    if (!audioFrame || !swrContext) {
        throw DecoderException("Invalid audio processing state");
    }

    auto src = audioFrame.get();

    if (src->format == AV_SAMPLE_FMT_NONE || src->sample_rate <= 0 || src->ch_layout.nb_channels <= 0) {
        throw DecoderException("Invalid source audio frame");
    }

    const int outSamples = swr_get_out_samples(swrContext.get(), src->nb_samples);

    if (outSamples < 0) {
        throw DecoderException("Failed to calculate output samples");
    }

    int bufferSize = av_samples_get_buffer_size(
            nullptr,
            audioCodecContext->ch_layout.nb_channels,
            outSamples,
            targetSampleFormat,
            1
    );

    if (bufferSize <= 0) {
        throw DecoderException("Invalid buffer size calculation");
    }

    bufferSize += AV_INPUT_BUFFER_PADDING_SIZE;

    if (audioBuffer.size() < bufferSize) {
        audioBuffer.resize(bufferSize);
    }

    auto ptr = audioBuffer.data();

    const int convertedSamples = swr_convert(
            swrContext.get(),
            &ptr,
            outSamples,
            const_cast<const uint8_t **>(src->data),
            src->nb_samples
    );

    if (convertedSamples < 0) {
        throw DecoderException("Audio conversion failed");
    }

    int actualSize = av_samples_get_buffer_size(
            nullptr,
            audioCodecContext->ch_layout.nb_channels,
            convertedSamples,
            targetSampleFormat,
            1
    );

    if (actualSize <= 0) {
        throw DecoderException("Invalid converted audio size");
    }

    return actualSize;
}

int Decoder::_processVideo(uint8_t *buffer) {
    if (!swVideoFrame || !swsContext) {
        throw DecoderException("Invalid video processing state");
    }

    auto src = swVideoFrame.get();

    if (src->format == AV_PIX_FMT_NONE ||
        src->width <= 0 ||
        src->height <= 0 ||
        !src->data[0] ||
        src->linesize[0] <= 0) {
        throw DecoderException("Invalid source video frame data");
    }

    if (src->width != swsWidth || src->height != swsHeight || src->format != swsPixelFormat) {
        auto newContext = sws_getCachedContext(
                swsContext.release(),
                src->width,
                src->height,
                static_cast<AVPixelFormat>(src->format),
                videoCodecContext->width,
                videoCodecContext->height,
                targetPixelFormat,
                swsFlags,
                nullptr,
                nullptr,
                nullptr
        );

        if (!newContext) {
            throw DecoderException("Could not allocate sws context");
        }

        swsContext.reset(newContext);

        swsWidth = src->width;

        swsHeight = src->height;

        swsPixelFormat = static_cast<AVPixelFormat>(src->format);
    }

    uint8_t *dst[4] = {buffer, nullptr, nullptr, nullptr};

    int linesize[4] = {0, 0, 0, 0};

    av_image_fill_linesizes(linesize, targetPixelFormat, videoCodecContext->width);

    if (linesize[0] <= 0) {
        throw DecoderException("Invalid destination linesize");
    }

    if (sws_scale(
            swsContext.get(),
            src->data,
            src->linesize,
            0,
            src->height,
            dst,
            linesize
    ) <= 0) {
        throw DecoderException("Video conversion failed");
    }

    int actualSize = av_image_get_buffer_size(
            targetPixelFormat,
            videoCodecContext->width,
            videoCodecContext->height,
            1
    );

    if (actualSize <= 0) {
        throw DecoderException("Invalid converted video size");
    }

    return actualSize;
}

Decoder::Decoder(
        const std::string &location,
        const bool findAudioStream,
        const bool findVideoStream,
        const bool decodeAudioStream,
        const bool decodeVideoStream,
        const std::vector<uint32_t> &hardwareAccelerationCandidates
) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    AVFormatContext *rawFormatContext = nullptr;

    if ((avformat_open_input(&rawFormatContext, location.c_str(), nullptr, nullptr) < 0) || !rawFormatContext) {
        throw DecoderException("Could not open input stream for location: " + location);
    }

    formatContext = std::unique_ptr<AVFormatContext, AVFormatContextDeleter>(rawFormatContext);

    if (avformat_find_stream_info(formatContext.get(), nullptr) < 0) {
        throw DecoderException("Could not find stream information");
    }

    formatContext->flags |= AVFMT_FLAG_GENPTS;

    format = {
            location,
            formatContext->duration < 0 || formatContext->duration & AV_NOPTS_VALUE ? 0 : formatContext->duration
    };

    for (int streamIndex = 0; streamIndex < formatContext->nb_streams; ++streamIndex) {
        auto stream = formatContext->streams[streamIndex];

        if (stream->codecpar->codec_type == AVMediaType::AVMEDIA_TYPE_AUDIO && findAudioStream) {
            if ((audioDecoder = avcodec_find_decoder(stream->codecpar->codec_id))) {
                if (!(audioStream = formatContext->streams[streamIndex])) {
                    throw DecoderException("Could not find audio stream");
                }

                audioCodecContext = std::unique_ptr<AVCodecContext, AVCodecContextDeleter>(
                        avcodec_alloc_context3(audioDecoder)
                );

                if (!audioCodecContext) {
                    throw DecoderException("Could not allocate audio codec context");
                }

                if (avcodec_parameters_to_context(audioCodecContext.get(), audioStream->codecpar) < 0) {
                    throw DecoderException("Could not copy parameters to audio codec context");
                }

                if (audioDecoder->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
                    audioCodecContext->thread_type = FF_THREAD_FRAME;

                    audioCodecContext->thread_count = THREAD_COUNT;
                } else if (audioDecoder->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
                    audioCodecContext->thread_type = FF_THREAD_SLICE;

                    audioCodecContext->thread_count = THREAD_COUNT;
                }

                audioCodecContext->flags |= AV_CODEC_FLAG_LOW_DELAY;

                if (avcodec_open2(audioCodecContext.get(), audioDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open audio decoder");
                }

                format.durationMicros = std::max(
                        format.durationMicros,
                        av_rescale_q(
                                audioStream->duration,
                                audioStream->time_base,
                                {1, AV_TIME_BASE}
                        )
                );

                format.sampleRate = audioCodecContext->sample_rate;

                format.channels = audioCodecContext->ch_layout.nb_channels;

                if (decodeAudioStream) {
                    SwrContext *rawSwrContext = nullptr;

                    if (swr_alloc_set_opts2(
                            &rawSwrContext,
                            &audioCodecContext->ch_layout,
                            targetSampleFormat,
                            audioCodecContext->sample_rate,
                            &audioCodecContext->ch_layout,
                            audioCodecContext->sample_fmt,
                            audioCodecContext->sample_rate,
                            0,
                            nullptr) < 0 || !rawSwrContext
                            ) {
                        throw DecoderException("Could not allocate swr context");
                    }

                    swrContext = std::unique_ptr<SwrContext, SwrContextDeleter>(rawSwrContext);

                    if (swr_init(swrContext.get()) < 0) {
                        throw DecoderException("Could not initialize swr context");
                    }

                    audioFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

                    if (!audioFrame) {
                        throw DecoderException("Memory allocation failed for audio frame");
                    }
                }
            }
        } else if (stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO && findVideoStream) {
            if ((videoDecoder = avcodec_find_decoder(stream->codecpar->codec_id))) {
                if (!(videoStream = formatContext->streams[streamIndex])) {
                    throw DecoderException("Could not find video stream");
                }

                videoCodecContext = std::unique_ptr<AVCodecContext, AVCodecContextDeleter>(
                        avcodec_alloc_context3(videoDecoder)
                );

                if (!videoCodecContext) {
                    throw DecoderException("Could not allocate video codec context");
                }

                if (avcodec_parameters_to_context(videoCodecContext.get(), videoStream->codecpar) < 0) {
                    throw DecoderException("Could not copy parameters to video codec context");
                }

                if (decodeVideoStream && !hardwareAccelerationCandidates.empty()) {
                    for (auto deviceType: hardwareAccelerationCandidates) {
                        if (_prepareHardwareAcceleration(deviceType)) {
                            videoCodecContext->get_format = _getHardwareAccelerationFormat;

                            format.hwDeviceType = static_cast<AVHWDeviceType>(deviceType);

                            break;
                        }
                    }
                }

                if (videoStream->avg_frame_rate.num != 0 && videoStream->avg_frame_rate.den != 0) {
                    format.frameRate = av_q2d(videoStream->avg_frame_rate);
                }

                const auto frameInterval = 1'000'000.0 / format.frameRate;

                if (format.frameRate > 0) {
                    if (static_cast<double>(format.durationMicros) > frameInterval) {
                        if (videoDecoder->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
                            videoCodecContext->thread_type = FF_THREAD_FRAME;

                            videoCodecContext->thread_count = THREAD_COUNT;
                        } else if (videoDecoder->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
                            videoCodecContext->thread_type = FF_THREAD_SLICE;

                            videoCodecContext->thread_count = THREAD_COUNT;
                        }
                    } else {
                        format.frameRate = 0.0;
                        format.durationMicros = 0;
                    }
                }

                videoCodecContext->flags |= AV_CODEC_FLAG_LOW_DELAY;

                if (avcodec_open2(videoCodecContext.get(), videoDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open video decoder");
                }

                format.durationMicros = std::max(
                        format.durationMicros,
                        av_rescale_q(
                                videoStream->duration,
                                videoStream->time_base,
                                {1, AV_TIME_BASE}
                        )
                );

                format.width = videoCodecContext->width;

                format.height = videoCodecContext->height;

                int videoBufferCapacity = av_image_get_buffer_size(
                        targetPixelFormat,
                        videoCodecContext->width,
                        videoCodecContext->height,
                        1
                );

                if (videoBufferCapacity <= 0) {
                    throw DecoderException("Invalid video buffer capacity");
                }

                videoBufferCapacity += AV_INPUT_BUFFER_PADDING_SIZE;

                format.videoBufferCapacity = videoBufferCapacity;

                if (decodeVideoStream) {
                    swsContext = std::unique_ptr<SwsContext, SwsContextDeleter>(
                            sws_getContext(
                                    videoCodecContext->width,
                                    videoCodecContext->height,
                                    videoCodecContext->pix_fmt,
                                    videoCodecContext->width,
                                    videoCodecContext->height,
                                    targetPixelFormat,
                                    swsFlags,
                                    nullptr,
                                    nullptr,
                                    nullptr
                            )
                    );

                    if (!swsContext) {
                        throw DecoderException("Could not allocate sws context");
                    }

                    swsWidth = videoCodecContext->width;

                    swsHeight = videoCodecContext->height;

                    swsPixelFormat = static_cast<AVPixelFormat>(videoCodecContext->pix_fmt);

                    swVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

                    if (!swVideoFrame) {
                        throw DecoderException("Memory allocation failed for software video frame");
                    }
                }
            }
        }
    }

    if (audioStream || videoStream) {
        packet = std::unique_ptr<AVPacket, AVPacketDeleter>(av_packet_alloc());

        if (!packet) {
            throw DecoderException("Memory allocation failed for packet");
        }
    }
}

Decoder::~Decoder() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (videoCodecContext && videoCodecContext->hw_device_ctx) {
        HardwareAcceleration::releaseContext(videoCodecContext->hw_device_ctx);

        videoCodecContext->hw_device_ctx = nullptr;
    }

    hwVideoFrame.reset();

    swVideoFrame.reset();

    audioFrame.reset();

    packet.reset();

    swsContext.reset();

    swrContext.reset();

    videoCodecContext.reset();

    audioCodecContext.reset();

    formatContext.reset();
}

std::optional<AudioFrame> Decoder::decodeAudio() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio()) {
        throw DecoderException("Could not find audio stream");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) >= 0) {
            if (packet->stream_index == audioStream->index) {
                if (avcodec_send_packet(audioCodecContext.get(), packet.get()) < 0) {
                    av_packet_unref(packet.get());

                    continue;
                }

                av_packet_unref(packet.get());

                while (true) {
                    int ret = avcodec_receive_frame(audioCodecContext.get(), audioFrame.get());

                    if (ret == AVERROR(EAGAIN)) {
                        break;
                    }

                    if (ret == AVERROR_EOF) {
                        break;
                    }

                    if (ret < 0) {
                        throw DecoderException("Error receiving audio frame");
                    }

                    const auto frameTimestampMicros = (audioFrame->best_effort_timestamp != AV_NOPTS_VALUE)
                                                      ? audioFrame->best_effort_timestamp : audioFrame->pts;

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            audioStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    auto remaining = _processAudio();

                    av_frame_unref(audioFrame.get());

                    std::vector<uint8_t> bytes(audioBuffer.begin(), audioBuffer.begin() + remaining);

                    return std::optional(
                            AudioFrame{
                                    bytes,
                                    timestampMicros
                            }
                    );
                }
            }

            av_packet_unref(packet.get());
        }
    } catch (...) {
        if (packet) {
            av_packet_unref(packet.get());
        }

        if (audioFrame) {
            av_frame_unref(audioFrame.get());
        }

        throw;
    }

    return std::nullopt;
}

std::optional<VideoFrame> Decoder::decodeVideo(uint8_t *buffer, int capacity) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasVideo()) {
        throw DecoderException("Could not find video stream");
    }

    if (!buffer) {
        throw DecoderException("Invalid buffer");
    }

    if (capacity <= 0) {
        throw DecoderException("Invalid buffer capacity");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) >= 0) {
            if (packet->stream_index == videoStream->index) {
                if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
                    av_packet_unref(packet.get());

                    continue;
                }

                av_packet_unref(packet.get());

                while (true) {
                    int ret = avcodec_receive_frame(
                            videoCodecContext.get(),
                            _isHardwareAccelerated() ? hwVideoFrame.get() : swVideoFrame.get()
                    );

                    if (ret == AVERROR(EAGAIN)) {
                        break;
                    }

                    if (ret == AVERROR_EOF) {
                        break;
                    }

                    if (ret < 0) {
                        throw DecoderException("Error receiving video frame");
                    }

                    if (_isHardwareAccelerated()) {
                        if (av_hwframe_transfer_data(swVideoFrame.get(), hwVideoFrame.get(), 0) < 0) {
                            throw DecoderException("Error transferring frame to system memory");
                        }

                        if (swVideoFrame->format == AV_PIX_FMT_NONE || !swVideoFrame->data[0]) {
                            throw DecoderException("Error transferring frame data");
                        }

                        swVideoFrame->best_effort_timestamp = hwVideoFrame->best_effort_timestamp;

                        swVideoFrame->pts = hwVideoFrame->pts;

                        av_frame_unref(hwVideoFrame.get());
                    }

                    const auto frameTimestampMicros = (swVideoFrame->best_effort_timestamp != AV_NOPTS_VALUE)
                                                      ? swVideoFrame->best_effort_timestamp : swVideoFrame->pts;

                    auto remaining = _processVideo(buffer);

                    av_frame_unref(swVideoFrame.get());

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            videoStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    return std::optional(
                            VideoFrame{
                                    remaining,
                                    timestampMicros
                            }
                    );
                }
            }

            av_packet_unref(packet.get());
        }
    } catch (...) {
        if (packet) {
            av_packet_unref(packet.get());
        }

        if (hwVideoFrame) {
            av_frame_unref(hwVideoFrame.get());
        }

        if (swVideoFrame) {
            av_frame_unref(swVideoFrame.get());
        }

        throw;
    }

    return std::nullopt;
}

void Decoder::seekTo(const long timestampMicros, const bool keyframesOnly) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    int seekStreamIndex;

    AVCodecContext *codecContext = nullptr;

    if (videoStream && videoCodecContext) {
        seekStreamIndex = videoStream->index;

        codecContext = videoCodecContext.get();
    } else if (audioStream && audioCodecContext) {
        seekStreamIndex = audioStream->index;

        codecContext = audioCodecContext.get();
    } else {
        throw DecoderException("No streams to seek");
    }

    const auto targetPts = av_rescale_q(
            timestampMicros,
            AVRational{1, AV_TIME_BASE},
            formatContext->streams[seekStreamIndex]->time_base
    );

    if (av_seek_frame(formatContext.get(), seekStreamIndex, targetPts, AVSEEK_FLAG_BACKWARD) < 0) {
        if (av_seek_frame(formatContext.get(), -1, timestampMicros, AVSEEK_FLAG_BACKWARD) < 0) {
            throw DecoderException("Error seeking stream");
        }
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext.get());
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (packet) {
        av_packet_unref(packet.get());
    }

    if (audioFrame) {
        av_frame_unref(audioFrame.get());
    }

    if (swVideoFrame) {
        av_frame_unref(swVideoFrame.get());
    }

    if (hwVideoFrame) {
        av_frame_unref(hwVideoFrame.get());
    }

    audioBuffer.clear();

    if (!keyframesOnly && codecContext) {
        const int64_t thresholdMicros = (videoStream) ? 20'000 : 50'000;

        const int64_t thresholdPts = av_rescale_q(
                thresholdMicros,
                AVRational{1, AV_TIME_BASE},
                formatContext->streams[seekStreamIndex]->time_base
        );

        auto tempPacket = std::unique_ptr<AVPacket, AVPacketDeleter>(av_packet_alloc());

        if (!tempPacket) {
            throw DecoderException("Memory allocation failed temporary packet");
        }

        auto tempFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

        if (!tempFrame) {
            throw DecoderException("Memory allocation failed temporary frame");
        }

        auto cleanUp = [&]() {
            tempFrame.reset();

            tempPacket.reset();
        };

        try {
            const int64_t fileDurationMs = format.durationMicros / 1000;

            int64_t frameDurationMs = 50;

            if (videoStream && seekStreamIndex == videoStream->index) {
                double frameRate = av_q2d(videoStream->avg_frame_rate);

                frameDurationMs = (frameRate > 0) ? static_cast<int64_t>(1000.0 / frameRate) : 16;
            }

            const int MAX_ITERATIONS = std::max(static_cast<long>((fileDurationMs / frameDurationMs) * 2L + 1000),
                                                1000L);

            int iterations = 0;

            int64_t lastPts = AV_NOPTS_VALUE;

            while (av_read_frame(formatContext.get(), tempPacket.get()) >= 0) {
                if (++iterations > MAX_ITERATIONS) {
                    break;
                }

                if (tempPacket->stream_index != seekStreamIndex) {
                    av_packet_unref(tempPacket.get());

                    continue;
                }

                if (tempPacket->pts != AV_NOPTS_VALUE) {
                    if (lastPts != AV_NOPTS_VALUE && tempPacket->pts <= lastPts) {
                        break;
                    }

                    lastPts = tempPacket->pts;
                }

                if (avcodec_send_packet(codecContext, tempPacket.get()) < 0) {
                    av_packet_unref(tempPacket.get());

                    continue;
                }

                while (true) {
                    int ret = avcodec_receive_frame(codecContext, tempFrame.get());

                    if (ret == AVERROR(EAGAIN)) {
                        break;
                    }

                    if (ret == AVERROR_EOF) {
                        break;
                    }

                    if (ret < 0) {
                        throw DecoderException("Error receiving temporary frame");
                    }

                    const int64_t framePts = (tempFrame->best_effort_timestamp != AV_NOPTS_VALUE)
                                             ? tempFrame->best_effort_timestamp : tempFrame->pts;

                    if (framePts >= targetPts - thresholdPts) {
                        av_packet_unref(tempPacket.get());

                        return;
                    }

                    av_frame_unref(tempFrame.get());
                }

                av_packet_unref(tempPacket.get());
            }

            cleanUp();
        } catch (...) {
            cleanUp();
        }
    }
}

void Decoder::reset() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (av_seek_frame(formatContext.get(), -1, 0, AVSEEK_FLAG_BACKWARD) < 0) {
        throw DecoderException("Error resetting stream");
    }

    if (videoCodecContext) avcodec_flush_buffers(videoCodecContext.get());

    if (audioCodecContext) avcodec_flush_buffers(audioCodecContext.get());

    if (packet) {
        av_packet_unref(packet.get());
    }

    if (audioFrame) {
        av_frame_unref(audioFrame.get());
    }

    if (swVideoFrame) {
        av_frame_unref(swVideoFrame.get());
    }

    if (hwVideoFrame) {
        av_frame_unref(hwVideoFrame.get());
    }

    audioBuffer.clear();
}