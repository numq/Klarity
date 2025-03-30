#include "decoder.h"

bool Decoder::_hasAudio() {
    return audioCodecContext &&
           audioStream &&
           audioStream->codecpar &&
           audioDecoder &&
           audioFrame &&
           audioCodecContext->sample_rate > 0 &&
           audioCodecContext->ch_layout.nb_channels > 0 &&
           format.sampleRate > 0 &&
           format.channels > 0;
}

bool Decoder::_hasVideo() {
    if (!videoCodecContext || !videoStream || !videoStream->codecpar || !videoDecoder || !swVideoFrame) {
        return false;
    }

    const bool validDimensions = videoCodecContext->pix_fmt != AV_PIX_FMT_NONE &&
                                 videoCodecContext->width > 0 &&
                                 videoCodecContext->height > 0 &&
                                 format.width > 0 &&
                                 format.height > 0;

    if (_isHardwareAccelerated()) {
        return validDimensions &&
               hwVideoFrame &&
               videoCodecContext->hw_device_ctx &&
               videoCodecContext->pix_fmt != AV_PIX_FMT_NONE;
    }

    return validDimensions && videoCodecContext->pix_fmt != AV_PIX_FMT_NONE;
}

bool Decoder::_isValid() {
    return formatContext && formatContext->pb && (_hasAudio() || _hasVideo());
}

bool Decoder::_isHardwareAccelerated() {
    return hwDeviceType != AV_HWDEVICE_TYPE_NONE &&
           videoCodecContext &&
           videoCodecContext->hw_device_ctx &&
           hwVideoFrame;
}

void Decoder::_prepareHardwareAcceleration() {
    if (hwDeviceType == AV_HWDEVICE_TYPE_NONE) {
        return;
    }

    const AVCodecHWConfig *config = nullptr;

    for (int i = 0; (config = avcodec_get_hw_config(videoDecoder, i)); i++) {
        if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX && config->device_type == hwDeviceType) {
            videoCodecContext->opaque = reinterpret_cast<void *>(static_cast<uintptr_t>(config->pix_fmt));

            break;
        }
    }

    videoCodecContext->hw_device_ctx = HardwareAcceleration::requestContext(hwDeviceType);

    hwVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

    if (!hwVideoFrame) {
        throw HardwareAccelerationException("Memory allocation failed for hardware video frame");
    }
}

void Decoder::_prepareSwsContext(
        AVPixelFormat srcFormat,
        uint32_t srcWidth,
        uint32_t srcHeight,
        uint32_t dstWidth,
        uint32_t dstHeight
) {
    if (swsPixelFormat != targetPixelFormat || swsWidth != dstWidth || swsHeight != dstHeight) {
        swsContext = std::unique_ptr<SwsContext, SwsContextDeleter>(
                sws_getCachedContext(
                        swsContext.release(),
                        static_cast<int>(srcWidth), static_cast<int>(srcHeight), srcFormat,
                        static_cast<int>(dstWidth), static_cast<int>(dstHeight), targetPixelFormat,
                        swsFlags,
                        nullptr, nullptr, nullptr
                )
        );

        if (!swsContext) {
            swsPixelFormat = AV_PIX_FMT_NONE;

            swsWidth = -1;

            swsHeight = -1;

            throw DecoderException("Could not reallocate sws context");
        }

        swsPixelFormat = targetPixelFormat;

        swsWidth = static_cast<int>(dstWidth);

        swsHeight = static_cast<int>(dstHeight);
    }
}

void Decoder::_processAudioFrame() {
    if (!audioFrame) {
        throw DecoderException("Invalid audio frame");
    }

    auto src = audioFrame.get();

    if (format.sampleRate <= 0 || format.channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    const auto nbSamples = av_rescale_rnd(
            swr_get_delay(swrContext.get(), src->sample_rate) + src->nb_samples,
            format.sampleRate,
            src->sample_rate,
            AV_ROUND_UP
    );

    int bufferSize;

    if ((bufferSize = av_samples_get_buffer_size(
            nullptr,
            src->ch_layout.nb_channels,
            static_cast<int>(nbSamples),
            targetSampleFormat,
            1
    )) < 0) {
        throw DecoderException("Could not get buffer size, error: " + std::to_string(bufferSize));
    }

    if (audioBuffer.size() != bufferSize) {
        audioBuffer.resize(bufferSize);
    }

    if (src->format != targetSampleFormat) {
        uint8_t *outPlanes[AV_NUM_DATA_POINTERS] = {nullptr};

        int filledSize;

        if ((filledSize = av_samples_fill_arrays(
                outPlanes,
                nullptr,
                audioBuffer.data(),
                src->ch_layout.nb_channels,
                static_cast<int>(nbSamples),
                targetSampleFormat,
                1
        )) < 0) {
            throw DecoderException("Error while filling audio buffer, error: " + std::to_string(filledSize));
        }

        swr_convert(
                swrContext.get(),
                outPlanes,
                static_cast<int>(nbSamples),
                const_cast<const uint8_t **>(src->data),
                src->nb_samples
        );
    } else {
        if (av_sample_fmt_is_planar(targetSampleFormat)) {
            for (int ch = 0; ch < src->ch_layout.nb_channels; ch++) {
                int channel_size;

                if ((channel_size = av_samples_get_buffer_size(
                        nullptr, 1, static_cast<int>(nbSamples), targetSampleFormat, 1
                )) < 0) {
                    throw DecoderException("Could not get per-channel buffer size");
                }

                memcpy(audioBuffer.data() + ch * channel_size, src->data[ch], channel_size);
            }
        } else {
            memcpy(audioBuffer.data(), src->data[0], bufferSize);
        }
    }
}

void Decoder::_processVideoFrame(uint32_t dstWidth, uint32_t dstHeight) {
    if (!swVideoFrame) {
        throw DecoderException("Invalid video frame");
    }

    auto src = swVideoFrame.get();

    if (static_cast<AVPixelFormat>(src->format) == AV_PIX_FMT_NONE) {
        throw DecoderException("Invalid pixel format");
    }

    if (format.width <= 0 || format.height <= 0) {
        throw DecoderException("Invalid video format");
    }

    if (dstWidth <= 0 || dstHeight <= 0) {
        throw DecoderException("Invalid destination dimensions");
    }

    auto srcFormat = static_cast<AVPixelFormat>(src->format);

    switch (src->format) {
        case AV_PIX_FMT_YUVJ420P:
            srcFormat = AV_PIX_FMT_YUV420P;
            break;

        case AV_PIX_FMT_YUVJ422P:
            srcFormat = AV_PIX_FMT_YUV422P;
            break;

        case AV_PIX_FMT_YUVJ444P:
            srcFormat = AV_PIX_FMT_YUV444P;
            break;

        default:
            break;
    }

    const auto srcWidth = src->width;

    const auto srcHeight = src->height;

    _prepareSwsContext(srcFormat, srcWidth, srcHeight, dstWidth, dstHeight);

    int bufferSize;

    if ((bufferSize = av_image_get_buffer_size(
            static_cast<AVPixelFormat>(swsPixelFormat),
            swsWidth,
            swsHeight,
            1
    )) < 0) {
        throw DecoderException("Could not get buffer size, error: " + std::to_string(bufferSize));
    }

    bufferSize += AV_INPUT_BUFFER_PADDING_SIZE;

    if (videoBuffer.size() != bufferSize) {
        videoBuffer.resize(bufferSize);
    }

    std::vector<int> dstLineSize(AV_NUM_DATA_POINTERS, 0);

    if (av_image_fill_linesizes(
            dstLineSize.data(),
            static_cast<AVPixelFormat>(swsPixelFormat),
            swsWidth
    ) < 0) {
        throw DecoderException("Could not fill line sizes");
    }

    std::vector<uint8_t *> dst(AV_NUM_DATA_POINTERS, nullptr);

    if (av_image_fill_pointers(
            dst.data(),
            static_cast<AVPixelFormat>(swsPixelFormat),
            swsHeight,
            videoBuffer.data(),
            dstLineSize.data()
    ) < 0) {
        throw DecoderException("Could not fill pointers");
    }

    if (sws_scale(
            swsContext.get(),
            src->data,
            src->linesize,
            0,
            srcHeight,
            dst.data(),
            dstLineSize.data()
    ) < 0) {
        throw DecoderException("Error while converting the video frame");
    }
}

void Decoder::_transferFrameData() {
    if (!swVideoFrame || !hwVideoFrame) {
        throw HardwareAccelerationException("Invalid transfer frames");
    }

    swVideoFrame->best_effort_timestamp = hwVideoFrame->best_effort_timestamp;

    if (av_hwframe_transfer_data(swVideoFrame.get(), hwVideoFrame.get(), 0) < 0) {
        throw HardwareAccelerationException("Error transferring frame to system memory");
    }
}

Decoder::Decoder(
        const std::string &location,
        const bool findAudioStream,
        const bool findVideoStream,
        const uint32_t hwDeviceType
) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (findVideoStream) {
        this->hwDeviceType = static_cast<AVHWDeviceType>(hwDeviceType);
    }

    AVFormatContext *rawFormatContext = nullptr;

    if ((avformat_open_input(&rawFormatContext, location.c_str(), nullptr, nullptr) < 0) || !rawFormatContext) {
        throw DecoderException("Could not open input stream for location: " + location);
    }

    formatContext = std::unique_ptr<AVFormatContext, AVFormatContextDeleter>(rawFormatContext);

    if (avformat_find_stream_info(formatContext.get(), nullptr) < 0) {
        throw DecoderException("Could not find stream information");
    }

    format = Format{
            location,
            formatContext->duration == AV_NOPTS_VALUE ? 0 : static_cast<uint64_t>(formatContext->duration)
    };

    if (findAudioStream) {
        int streamIndex = av_find_best_stream(
                formatContext.get(),
                AVMediaType::AVMEDIA_TYPE_AUDIO,
                -1,
                -1,
                &audioDecoder,
                0
        );

        if (audioDecoder) {
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

            audioFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

            if (!audioFrame) {
                throw DecoderException("Memory allocation failed for audio frame");
            }

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

            format.sampleRate = audioCodecContext->sample_rate;

            format.channels = audioCodecContext->ch_layout.nb_channels;
        }
    }

    if (findVideoStream) {
        int streamIndex = av_find_best_stream(
                formatContext.get(),
                AVMediaType::AVMEDIA_TYPE_VIDEO,
                -1,
                -1,
                &videoDecoder,
                0
        );

        if (videoDecoder) {
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

            _prepareHardwareAcceleration();

            if (avcodec_open2(videoCodecContext.get(), videoDecoder, nullptr) < 0) {
                throw DecoderException("Could not open video decoder");
            }

            swVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

            if (!swVideoFrame) {
                throw DecoderException("Memory allocation failed for software video frame");
            }

            swsContext = std::unique_ptr<SwsContext, SwsContextDeleter>(
                    sws_getContext(
                            videoCodecContext->width, videoCodecContext->height, videoCodecContext->pix_fmt,
                            videoCodecContext->width, videoCodecContext->height, targetPixelFormat,
                            swsFlags,
                            nullptr, nullptr, nullptr
                    )
            );

            if (!swsContext) {
                throw DecoderException("Could not allocate sws context");
            }

            swsPixelFormat = videoCodecContext->pix_fmt;

            swsWidth = videoCodecContext->width;

            swsHeight = videoCodecContext->height;

            format.width = videoCodecContext->width;

            format.height = videoCodecContext->height;

            format.frameRate = av_q2d(videoStream->avg_frame_rate);
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
}

std::optional<Frame> Decoder::decode(uint32_t width, uint32_t height) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio() && !_hasVideo()) {
        return std::nullopt;
    }

    av_packet_unref(packet.get());

    while (av_read_frame(formatContext.get(), packet.get()) == 0) {
        if (_hasAudio() && packet->stream_index == audioStream->index) {
            if (avcodec_send_packet(audioCodecContext.get(), packet.get()) < 0) {
                av_packet_unref(packet.get());

                continue;
            }

            av_frame_unref(audioFrame.get());

            int ret;

            while ((ret = avcodec_receive_frame(audioCodecContext.get(), audioFrame.get())) == 0) {
                const auto frameTimestampMicros = audioFrame->best_effort_timestamp;

                const auto timestampMicros = frameTimestampMicros == AV_NOPTS_VALUE ? 0 : av_rescale_q(
                        frameTimestampMicros,
                        audioStream->time_base,
                        AVRational{1, 1'000'000}
                );

                _processAudioFrame();

                return Frame{Frame::Type::AUDIO, timestampMicros, audioBuffer};
            }

            if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                throw DecoderException("Error while receiving audio frame");
            }
        } else if (_hasVideo() && packet->stream_index == videoStream->index) {
            if (packet->stream_index == videoStream->index) {
                if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
                    av_packet_unref(packet.get());

                    continue;
                }

                if (_isHardwareAccelerated()) {
                    av_frame_unref(hwVideoFrame.get());
                }

                av_frame_unref(swVideoFrame.get());

                int ret;
                while ((ret = avcodec_receive_frame(
                        videoCodecContext.get(),
                        _isHardwareAccelerated() ? hwVideoFrame.get() : swVideoFrame.get()
                )) == 0) {
                    if (_isHardwareAccelerated()) {
                        _transferFrameData();
                    }

                    _processVideoFrame(width, height);

                    const auto frameTimestampMicros = swVideoFrame->best_effort_timestamp;

                    const auto timestampMicros = frameTimestampMicros == AV_NOPTS_VALUE ? 0 : av_rescale_q(
                            frameTimestampMicros,
                            videoStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    return Frame{Frame::Type::VIDEO, timestampMicros, videoBuffer};
                }

                if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    throw DecoderException("Error while receiving video frame");
                }
            }
        }

        av_packet_unref(packet.get());
    }

    return std::nullopt;
}

void Decoder::seekTo(long timestampMicros, bool keyframesOnly) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio() && !_hasVideo()) {
        return;
    }

    if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    int streamIndex;

    if (_hasVideo()) {
        streamIndex = videoStream->index;
    } else {
        streamIndex = audioStream->index;
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

    if (packet) av_packet_unref(packet.get());

    if (audioFrame) av_frame_unref(audioFrame.get());

    if (hwVideoFrame) av_frame_unref(hwVideoFrame.get());

    if (swVideoFrame) av_frame_unref(swVideoFrame.get());

    if (av_seek_frame(formatContext.get(), streamIndex, targetTimestamp, seekFlags) < 0) {
        throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
    }

    if (_hasAudio()) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (_hasVideo()) {
        avcodec_flush_buffers(videoCodecContext.get());

        if (!keyframesOnly) {
            while (av_read_frame(formatContext.get(), packet.get()) == 0) {
                if (packet->stream_index == videoStream->index) {
                    if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
                        av_packet_unref(packet.get());

                        continue;
                    }

                    av_frame_unref(swVideoFrame.get());

                    if ((avcodec_receive_frame(videoCodecContext.get(), swVideoFrame.get())) >= 0) {
                        break;
                    }
                }

                av_packet_unref(packet.get());
            }
        }
    }
}

void Decoder::reset() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio() && !_hasVideo()) {
        return;
    }

    if (packet) av_packet_unref(packet.get());

    if (audioFrame) av_frame_unref(audioFrame.get());

    if (swVideoFrame) av_frame_unref(swVideoFrame.get());

    if (hwVideoFrame) av_frame_unref(hwVideoFrame.get());

    if (av_seek_frame(formatContext.get(), -1, -100, AVSEEK_FLAG_ANY) < 0) {
        throw DecoderException("Error resetting stream");
    }

    if (_hasAudio()) {
        avcodec_flush_buffers(audioCodecContext.get());

        audioBuffer.clear();
    }

    if (_hasVideo()) {
        avcodec_flush_buffers(videoCodecContext.get());

        videoBuffer.clear();
    }
}