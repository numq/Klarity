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

    if (!audioStream->codecpar || audioStream->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
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

                audioCodecContext->thread_count = 0;

                if (audioDecoder->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
                    audioCodecContext->thread_type = FF_THREAD_FRAME;
                } else if (audioDecoder->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
                    audioCodecContext->thread_type = FF_THREAD_SLICE;
                }

                if (avcodec_open2(audioCodecContext.get(), audioDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open audio decoder");
                }

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

                videoCodecContext->thread_count = 0;

                if (videoDecoder->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
                    videoCodecContext->thread_type = FF_THREAD_FRAME;
                } else if (videoDecoder->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
                    videoCodecContext->thread_type = FF_THREAD_SLICE;
                }

                if (avcodec_open2(videoCodecContext.get(), videoDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open video decoder");
                }

                format.width = videoCodecContext->width;

                format.height = videoCodecContext->height;

                if (videoStream->avg_frame_rate.num != 0 && videoStream->avg_frame_rate.den != 0) {
                    format.frameRate = av_q2d(videoStream->avg_frame_rate);
                }

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

                int ret;

                while ((ret = avcodec_receive_frame(audioCodecContext.get(), audioFrame.get())) >= 0) {
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

                if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    throw DecoderException("Error while receiving audio frame");
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

                int ret;

                while ((ret = avcodec_receive_frame(
                        videoCodecContext.get(),
                        _isHardwareAccelerated() ? hwVideoFrame.get() : swVideoFrame.get()
                )) >= 0) {
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

                if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    throw DecoderException("Error while receiving video frame");
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

uint64_t Decoder::seekTo(const long timestampMicros, const bool keyframesOnly) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    if (format.durationMicros == 0 || (!_hasAudio() && !_hasVideo())) {
        return 0;
    }

    int streamIndex = (audioStream ? audioStream : videoStream)->index;

    if (keyframesOnly && !keyframeCache.empty()) {
        auto it = keyframeCache.lower_bound(timestampMicros);

        if (it != keyframeCache.begin() &&
            (it == keyframeCache.end() || timestampMicros - std::prev(it)->first < it->first - timestampMicros)) {
            --it;
        }

        const auto cachedTimestamp = it->second;

        const auto cachedTarget = av_rescale_q(
                cachedTimestamp,
                {1, AV_TIME_BASE},
                formatContext->streams[streamIndex]->time_base
        );

        if (av_seek_frame(formatContext.get(), streamIndex, cachedTarget, AVSEEK_FLAG_BACKWARD) >= 0) {
            return cachedTimestamp;
        }
    }

    const auto targetTimestamp = av_rescale_q(
            timestampMicros,
            {1, AV_TIME_BASE},
            formatContext->streams[streamIndex]->time_base
    );

    int seekFlags = AVSEEK_FLAG_BACKWARD;

    if (!keyframesOnly) {
        seekFlags |= AVSEEK_FLAG_ANY;
    }

    if (keyframesOnly) {
        if (av_seek_frame(formatContext.get(), streamIndex, targetTimestamp, seekFlags) < 0) {
            throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
        }

        keyframeCache[timestampMicros] = targetTimestamp;

        return timestampMicros;
    }

    int64_t low = 0;

    int64_t high = format.durationMicros;

    int64_t best_match = timestampMicros;

    const int MAX_SEEK_ATTEMPTS = 5;

    int attempts = 0;

    while (low <= high && attempts++ < MAX_SEEK_ATTEMPTS) {
        int64_t mid = low + (high - low) / 2;

        const auto midTimestamp = av_rescale_q(
                mid,
                {1, AV_TIME_BASE},
                formatContext->streams[streamIndex]->time_base
        );

        if (av_seek_frame(formatContext.get(), streamIndex, midTimestamp, seekFlags) < 0) {
            high = mid - 1;

            continue;
        }

        long actualTimestamp;

        auto frame = av_frame_alloc();

        const int MAX_FRAMES_TO_READ = 10;

        int framesRead = 0;

        try {
            while (framesRead++ < MAX_FRAMES_TO_READ && av_read_frame(formatContext.get(), packet.get()) == 0) {
                if (packet->stream_index == streamIndex) {
                    auto codecContext = _hasVideo() ? videoCodecContext.get() : audioCodecContext.get();

                    if (avcodec_send_packet(codecContext, packet.get()) < 0) {
                        av_packet_unref(packet.get());

                        continue;
                    }

                    av_packet_unref(packet.get());

                    int ret = avcodec_receive_frame(codecContext, frame);
                    if (ret >= 0) {
                        int64_t frameTimestamp =
                                frame->best_effort_timestamp != AV_NOPTS_VALUE ? frame->best_effort_timestamp
                                                                               : frame->pts;

                        if (frameTimestamp != AV_NOPTS_VALUE) {
                            actualTimestamp = av_rescale_q(
                                    frameTimestamp,
                                    formatContext->streams[streamIndex]->time_base,
                                    {1, AV_TIME_BASE}
                            );

                            if (abs(actualTimestamp - timestampMicros) < abs(best_match - timestampMicros)) {
                                best_match = actualTimestamp;
                            }

                            if (actualTimestamp < timestampMicros) {
                                low = mid + 1;
                            } else {
                                high = mid - 1;
                            }

                            break;
                        }
                    }
                } else {
                    av_packet_unref(packet.get());
                }
            }

            av_frame_free(&frame);
        } catch (...) {
            av_frame_free(&frame);
            throw;
        }
    }

    const auto bestTimestamp = av_rescale_q(
            best_match,
            {1, AV_TIME_BASE},
            formatContext->streams[streamIndex]->time_base
    );

    if (av_seek_frame(formatContext.get(), streamIndex, bestTimestamp, seekFlags) < 0) {
        throw DecoderException("Error seeking to best match timestamp");
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

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext.get());
    }

    audioBuffer.clear();

    audioBuffer.shrink_to_fit();

    return best_match;
}

void Decoder::reset() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (format.durationMicros == 0 || (!_hasAudio() && !_hasVideo())) {
        return;
    }

    if (av_seek_frame(formatContext.get(), -1, 0, AVSEEK_FLAG_ANY) < 0) {
        throw DecoderException("Error resetting stream");
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

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext.get());
    }

    audioBuffer.clear();

    audioBuffer.shrink_to_fit();
}