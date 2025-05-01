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

    if (!audioFrame || !audioBufferPool) {
        return false;
    }

    return true;
}

bool Decoder::_hasVideo() {
    if (!videoStream || !videoCodecContext || !videoDecoder) {
        return false;
    }

    if (!videoStream->codecpar || videoStream->codecpar->codec_type != AVMEDIA_TYPE_VIDEO) {
        return false;
    }

    if (videoCodecContext->pix_fmt == AV_PIX_FMT_NONE) {
        return false;
    }

    if (videoCodecContext->width <= 0 || videoCodecContext->height <= 0) {
        return false;
    }

    if (!swVideoFrame || !videoBufferPool) {
        return false;
    }

    return true;
}

bool Decoder::_isValid() {
    return formatContext && (audioStream || videoStream);
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

void Decoder::_processAudio(std::vector<uint8_t> &dst) {
    if (!audioFrame || !swrContext) {
        throw DecoderException("Invalid audio processing state");
    }

    auto src = audioFrame.get();

    if (src->format == AV_SAMPLE_FMT_NONE || src->sample_rate <= 0 || src->ch_layout.nb_channels <= 0) {
        throw DecoderException("Invalid source audio frame");
    }

    auto outSamples = av_rescale_rnd(
            swr_get_delay(swrContext.get(), src->sample_rate) + src->nb_samples,
            targetSampleRate,
            src->sample_rate,
            AV_ROUND_UP
    );

    if (outSamples <= 0) {
        throw DecoderException("Invalid output sample count");
    }

    outSamples += AV_INPUT_BUFFER_PADDING_SIZE;

    const int bufferSize = av_samples_get_buffer_size(
            nullptr,
            targetChannelLayout.nb_channels,
            static_cast<int>(outSamples),
            targetSampleFormat,
            1
    );

    if (bufferSize <= 0) {
        throw DecoderException("Invalid buffer size calculation");
    }

    dst.resize(bufferSize);

    auto out = dst.data();

    const int convertedSamples = swr_convert(
            swrContext.get(),
            &out,
            static_cast<int>(outSamples),
            const_cast<const uint8_t **>(src->data),
            src->nb_samples
    );

    if (convertedSamples < 0) {
        throw DecoderException("Audio conversion failed");
    }

    int actualSize = av_samples_get_buffer_size(
            nullptr,
            targetChannelLayout.nb_channels,
            convertedSamples,
            targetSampleFormat,
            1
    );

    if (actualSize <= 0) {
        throw DecoderException("Invalid converted audio size");
    }

    dst.resize(actualSize);
}

void Decoder::_processVideo(std::vector<uint8_t> &dst, uint8_t *const *planes, const int *strides) {
    if (!swVideoFrame || !swsContext) {
        throw DecoderException("Invalid video processing state");
    }

    auto src = swVideoFrame.get();

    if (src->format == AV_PIX_FMT_NONE || src->width <= 0 || src->height <= 0) {
        throw DecoderException("Invalid source video frame");
    }

    if (src->width != swsWidth || src->height != swsHeight || src->format != swsPixelFormat) {
        swsContext.reset(sws_getCachedContext(
                swsContext.release(),
                src->width,
                src->height,
                static_cast<AVPixelFormat>(src->format),
                targetWidth,
                targetHeight,
                targetPixelFormat,
                swsFlags,
                nullptr,
                nullptr,
                nullptr
        ));

        if (!swsContext) {
            throw DecoderException("Could not allocate sws context");
        }

        av_opt_set(swsContext.get(), "threads", "auto", 0);

        swsPixelFormat = static_cast<AVPixelFormat>(src->format);

        swsWidth = src->width;

        swsHeight = src->height;
    }

    if (sws_scale(
            swsContext.get(),
            src->data,
            src->linesize,
            0,
            src->height,
            planes,
            strides
    ) <= 0) {
        throw DecoderException("Video conversion failed");
    }

    int actualSize = av_image_get_buffer_size(
            targetPixelFormat,
            targetWidth,
            targetHeight,
            1
    );

    if (actualSize <= 0) {
        throw DecoderException("Invalid converted video size");
    }

    dst.resize(actualSize);
}

Decoder::Decoder(
        const std::string &location,
        const int audioFramePoolCapacity,
        const int videoFramePoolCapacity,
        const int sampleRate,
        const int channels,
        const int width,
        const int height,
        const std::vector<uint32_t> &hardwareAccelerationCandidates
) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    auto findAudioStream = audioFramePoolCapacity >= 0;

    auto findVideoStream = videoFramePoolCapacity >= 0;

    auto decodeAudioStream = audioFramePoolCapacity > 0;

    auto decodeVideoStream = videoFramePoolCapacity > 0;

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
            formatContext->duration
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

                if (avcodec_open2(audioCodecContext.get(), audioDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open audio decoder");
                }

                targetSampleRate = audioCodecContext->sample_rate;

                format.sampleRate = targetSampleRate;

                av_channel_layout_copy(&targetChannelLayout, &audioCodecContext->ch_layout);

                format.channels = targetChannelLayout.nb_channels;

                if (decodeAudioStream) {
                    audioCodecContext->thread_count = static_cast<int>(
                            std::min(16U, static_cast<unsigned int>(std::thread::hardware_concurrency()))
                    );

                    if (audioDecoder->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
                        audioCodecContext->thread_type = FF_THREAD_FRAME;
                    } else if (audioDecoder->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
                        audioCodecContext->thread_type = FF_THREAD_SLICE;
                    } else {
                        audioCodecContext->thread_count = 1;
                    }

                    if (sampleRate > 0) {
                        targetSampleRate = sampleRate;

                        format.sampleRate = targetSampleRate;
                    }

                    if (channels > 0) {
                        av_channel_layout_uninit(&targetChannelLayout);

                        av_channel_layout_default(&targetChannelLayout, channels);

                        format.channels = targetChannelLayout.nb_channels;
                    }

                    SwrContext *rawSwrContext = nullptr;

                    if (swr_alloc_set_opts2(
                            &rawSwrContext,
                            &targetChannelLayout,
                            targetSampleFormat,
                            targetSampleRate,
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

                    audioBufferPool = std::make_unique<AudioBufferPool>(audioFramePoolCapacity);
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

                videoCodecContext->get_format = _getHardwareAccelerationFormat;

                if (decodeVideoStream && !hardwareAccelerationCandidates.empty()) {
                    for (auto deviceType: hardwareAccelerationCandidates) {
                        if (_prepareHardwareAcceleration(deviceType)) {
                            format.hwDeviceType = static_cast<AVHWDeviceType>(deviceType);

                            break;
                        }
                    }
                }

                if (avcodec_open2(videoCodecContext.get(), videoDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open video decoder");
                }

                targetWidth = videoCodecContext->width;

                format.width = targetWidth;

                targetHeight = videoCodecContext->height;

                format.height = targetHeight;

                if (videoStream->avg_frame_rate.num != 0 && videoStream->avg_frame_rate.den != 0) {
                    format.frameRate = av_q2d(videoStream->avg_frame_rate);
                }

                if (decodeVideoStream) {
                    videoCodecContext->thread_count = static_cast<int>(
                            std::min(16U, static_cast<unsigned int>(std::thread::hardware_concurrency()))
                    );

                    if (videoDecoder->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
                        videoCodecContext->thread_type = FF_THREAD_FRAME;
                    } else if (videoDecoder->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
                        videoCodecContext->thread_type = FF_THREAD_SLICE;
                    } else {
                        videoCodecContext->thread_count = 1;
                    }

                    if (width > 0) {
                        targetWidth = width;

                        format.width = targetWidth;
                    }

                    if (height > 0) {
                        targetHeight = height;

                        format.height = targetHeight;
                    }

                    swsContext = std::unique_ptr<SwsContext, SwsContextDeleter>(
                            sws_getContext(
                                    videoCodecContext->width,
                                    videoCodecContext->height,
                                    videoCodecContext->pix_fmt,
                                    targetWidth,
                                    targetHeight,
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

                    av_opt_set(swsContext.get(), "threads", "auto", 0);

                    swsPixelFormat = static_cast<AVPixelFormat>(videoCodecContext->pix_fmt);

                    swsWidth = videoCodecContext->width;

                    swsHeight = videoCodecContext->height;

                    swVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

                    if (!swVideoFrame) {
                        throw DecoderException("Memory allocation failed for software video frame");
                    }

                    videoBufferPool = std::make_unique<VideoBufferPool>(
                            videoFramePoolCapacity,
                            targetWidth,
                            targetHeight,
                            targetPixelFormat
                    );
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

    videoBufferPool.reset();

    audioBufferPool.reset();

    packet.reset();

    swsContext.reset();

    swrContext.reset();

    av_channel_layout_uninit(&targetChannelLayout);

    videoCodecContext.reset();

    audioCodecContext.reset();

    formatContext.reset();
}

std::unique_ptr<Frame> Decoder::decodeAudio() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio()) {
        throw DecoderException("Could not find audio stream");
    }

    auto poolItem = audioBufferPool->acquire();

    if (!poolItem) {
        throw DecoderException("Could not get item from audio buffer pool");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) >= 0) {
            if (packet->stream_index == audioStream->index) {
                if (avcodec_send_packet(audioCodecContext.get(), packet.get()) < 0) {
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

                    _processAudio(poolItem->buffer);

                    av_frame_unref(audioFrame.get());

                    auto buffer = poolItem->buffer.data();

                    auto size = static_cast<int>(poolItem->buffer.size());

                    return std::make_unique<Frame>(
                            buffer,
                            size,
                            timestampMicros,
                            FrameType::AUDIO
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

    return nullptr;
}

std::unique_ptr<Frame> Decoder::decodeVideo() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasVideo()) {
        throw DecoderException("Could not find video stream");
    }

    auto poolItem = videoBufferPool->acquire();

    if (!poolItem) {
        throw DecoderException("Could not get item from video buffer pool");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) >= 0) {
            if (packet->stream_index == videoStream->index) {
                if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
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

                    _processVideo(poolItem->buffer, poolItem->planes.data(), poolItem->strides.data());

                    av_frame_unref(swVideoFrame.get());

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            videoStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    auto data = poolItem->buffer.data();

                    auto size = static_cast<int>(poolItem->buffer.size());

                    return std::make_unique<Frame>(
                            data,
                            size,
                            timestampMicros,
                            FrameType::VIDEO
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

    return nullptr;
}

uint64_t Decoder::seekTo(const long timestampMicros, const bool keyframesOnly) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (format.durationMicros == 0 || (!_hasAudio() && !_hasVideo())) {
        return 0;
    }

    if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    int streamIndex = 0;

    const auto targetTimestamp = av_rescale_q(
            timestampMicros,
            {1, AV_TIME_BASE},
            formatContext->streams[streamIndex]->time_base
    );

    int seekFlags = AVSEEK_FLAG_BACKWARD;

    if (!keyframesOnly) {
        seekFlags |= AVSEEK_FLAG_ANY;
    }

    if (av_seek_frame(formatContext.get(), streamIndex, targetTimestamp, seekFlags) < 0) {
        throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
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

        long actualTimestamp = -1;

        auto frame = av_frame_alloc();

        if (!frame) {
            throw DecoderException("Failed to allocate seeking frame");
        }

        try {
            while (av_read_frame(formatContext.get(), packet.get()) == 0) {
                if (packet->stream_index == streamIndex) {
                    auto codecContext = videoCodecContext.get();

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

                            break;
                        }
                    }
                } else {
                    av_packet_unref(packet.get());
                }
            }

            av_frame_free(&frame);

            if (actualTimestamp < 0) {
                return timestampMicros;
            }

            return actualTimestamp;
        } catch (...) {
            av_frame_free(&frame);
            throw;
        }
    }

    return timestampMicros;
}

void Decoder::reset() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio() && !_hasVideo()) {
        return;
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

    if (av_seek_frame(formatContext.get(), -1, 0, AVSEEK_FLAG_ANY) < 0) {
        throw DecoderException("Error resetting stream");
    }

    if (_hasAudio()) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (_hasVideo()) {
        avcodec_flush_buffers(videoCodecContext.get());
    }
}

void Decoder::releaseAudioBuffer(void *buffer) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    audioBufferPool->release(reinterpret_cast<uint8_t *>(buffer));
}

void Decoder::releaseVideoBuffer(void *buffer) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    videoBufferPool->release(reinterpret_cast<uint8_t *>(buffer));
}