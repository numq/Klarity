#include "decoder.h"

bool Decoder::_hasAudio() {
    if (!audioCodecContext || !audioStream || !audioStream->codecpar || !audioDecoder || !audioFrame) {
        return false;
    }

    return format.sampleRate > 0 && format.channels > 0;
}

bool Decoder::_hasVideo() {
    if (!videoCodecContext || !videoStream || !videoStream->codecpar || !videoDecoder || !swVideoFrame) {
        return false;
    }

    return format.width > 0 && format.height > 0 && videoCodecContext->pix_fmt != AV_PIX_FMT_NONE;
}

bool Decoder::_isValid() {
    return formatContext && (audioStream || videoStream);
}

bool Decoder::_isHardwareAccelerated() {
    if (!videoCodecContext || !videoCodecContext->hw_device_ctx || !hwVideoFrame) {
        return false;
    }

    return format.hwDeviceType != AVHWDeviceType::AV_HWDEVICE_TYPE_NONE;
}

bool Decoder::_prepareHardwareAcceleration(const uint32_t deviceType) {
    if (!videoCodecContext || !videoDecoder || deviceType == AVHWDeviceType::AV_HWDEVICE_TYPE_NONE) {
        return false;
    }

    const AVCodecHWConfig *config = nullptr;

    for (int i = 0; (config = avcodec_get_hw_config(videoDecoder, i)); i++) {
        if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX && config->device_type == deviceType) {
            break;
        }
    }

    if (!config) {
        return false;
    }

    hwVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

    if (!hwVideoFrame) {
        throw HardwareAccelerationException("Memory allocation failed for hardware video frame");
    }

    auto targetDeviceType = static_cast<AVHWDeviceType>(deviceType);

    videoCodecContext->hw_device_ctx = HardwareAcceleration::requestContext(targetDeviceType);

    format.hwDeviceType = targetDeviceType;

    return true;
}

uint32_t Decoder::_processAudioFrame(uint8_t *buffer, const uint32_t bufferSize) {
    if (format.audioBufferSize <= 0 || bufferSize < format.audioBufferSize) {
        throw DecoderException("Invalid audio buffer size");
    }

    if (format.sampleRate <= 0 || format.channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    if (!audioFrame) {
        throw DecoderException("Invalid audio frame");
    }

    auto src = audioFrame.get();

    const auto nbSamples = av_rescale_rnd(
            swr_get_delay(swrContext.get(), src->sample_rate) + src->nb_samples,
            format.sampleRate,
            src->sample_rate,
            AV_ROUND_UP
    );

    int requiredBufferSize;

    if ((requiredBufferSize = av_samples_get_buffer_size(
            nullptr,
            src->ch_layout.nb_channels,
            static_cast<int>(nbSamples),
            targetSampleFormat,
            1
    )) < 0) {
        throw DecoderException("Could not get audio buffer size, error: " + std::to_string(bufferSize));
    }

    if (src->format != targetSampleFormat) {
        uint8_t *outPlanes[AV_NUM_DATA_POINTERS] = {nullptr};

        int filledSize;

        if ((filledSize = av_samples_fill_arrays(
                outPlanes,
                nullptr,
                buffer,
                src->ch_layout.nb_channels,
                static_cast<int>(nbSamples),
                targetSampleFormat,
                1
        )) < 0) {
            throw DecoderException("Error while filling audio buffer, error: " + std::to_string(filledSize));
        }

        uint32_t convertedSamples;

        if ((convertedSamples = swr_convert(
                swrContext.get(),
                outPlanes,
                static_cast<int>(nbSamples),
                const_cast<const uint8_t **>(src->data),
                src->nb_samples
        )) <= 0) {
            throw DecoderException("Error while converting the audio frame");
        }

        return convertedSamples * format.channels * av_get_bytes_per_sample(targetSampleFormat);
    } else {
        if (av_sample_fmt_is_planar(targetSampleFormat)) {
            const int bytesPerSample = av_get_bytes_per_sample(targetSampleFormat);

            const int planeSize = static_cast<int>(nbSamples) * bytesPerSample;

            auto totalCopied = 0;

            for (int ch = 0; ch < format.channels; ch++) {
                const int channel_size = std::min(planeSize, static_cast<int>(bufferSize) - totalCopied);

                memcpy(buffer + totalCopied, src->data[ch], channel_size);

                totalCopied += channel_size;
            }
            return totalCopied;
        } else {
            const int frameSize = std::min(requiredBufferSize, static_cast<int>(bufferSize));

            memcpy(buffer, src->data[0], frameSize);

            return frameSize;
        }
    }
}

uint32_t Decoder::_processVideoFrame(uint8_t *buffer, const uint32_t bufferSize) {
    if (format.videoBufferSize <= 0 || bufferSize < format.videoBufferSize) {
        throw DecoderException("Invalid video buffer size");
    }

    if (format.width <= 0 || format.height <= 0) {
        throw DecoderException("Invalid video format");
    }

    if (!swVideoFrame) {
        throw DecoderException("Invalid video frame");
    }

    auto src = swVideoFrame.get();

    if (static_cast<AVPixelFormat>(src->format) == AV_PIX_FMT_NONE) {
        throw DecoderException("Invalid pixel format");
    }

    const auto dstWidth = static_cast<int>(format.width);

    const auto dstHeight = static_cast<int>(format.height);

    std::vector<int> dstLineSize(AV_NUM_DATA_POINTERS, 0);

    if (av_image_fill_linesizes(
            dstLineSize.data(),
            targetPixelFormat,
            dstWidth
    ) < 0) {
        throw DecoderException("Could not fill line sizes");
    }

    std::vector<uint8_t *> dst(AV_NUM_DATA_POINTERS, nullptr);

    if (av_image_fill_pointers(
            dst.data(),
            targetPixelFormat,
            dstHeight,
            buffer,
            dstLineSize.data()
    ) <= 0) {
        throw DecoderException("Could not fill pointers");
    }

    uint32_t scaledHeight;

    if ((scaledHeight = sws_scale(
            swsContext.get(),
            src->data,
            src->linesize,
            0,
            src->height,
            dst.data(),
            dstLineSize.data()
    )) <= 0) {
        throw DecoderException("Error while converting the video frame");
    }

    return av_image_get_buffer_size(
            targetPixelFormat,
            static_cast<int>(format.width),
            static_cast<int>(scaledHeight),
            1
    );
}

Decoder::Decoder(
        const std::string &location,
        const bool findAudioStream,
        const bool findVideoStream,
        const bool decodeAudioStream,
        const bool decodeVideoStream,
        const uint32_t sampleRate,
        const uint32_t channels,
        const uint32_t width,
        const uint32_t height,
        const double frameRate,
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
            formatContext->duration == AV_NOPTS_VALUE ? 0 : static_cast<uint64_t>(formatContext->duration)
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

                format.sampleRate = audioCodecContext->sample_rate;

                format.channels = audioCodecContext->ch_layout.nb_channels;

                if (decodeAudioStream) {
                    if (sampleRate > 0) {
                        format.sampleRate = sampleRate;
                    }

                    if (channels > 0) {
                        format.channels = channels;
                    }

                    AVChannelLayout channelLayout = {};

                    av_channel_layout_default(&channelLayout, static_cast<int>(format.channels));

                    try {
                        SwrContext *rawSwrContext = nullptr;

                        if (swr_alloc_set_opts2(
                                &rawSwrContext,
                                &channelLayout,
                                targetSampleFormat,
                                static_cast<int>(format.sampleRate),
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
                    } catch (...) {
                        av_channel_layout_uninit(&channelLayout);

                        throw;
                    }

                    av_channel_layout_uninit(&channelLayout);

                    auto nbSamples = audioCodecContext->frame_size > 0 ? audioCodecContext->frame_size : 8192;

                    if (audioCodecContext->sample_rate != format.sampleRate) {
                        auto delay = swr_get_delay(swrContext.get(), audioCodecContext->sample_rate);

                        nbSamples = static_cast<int>(av_rescale_rnd(
                                delay + nbSamples,
                                format.sampleRate,
                                audioCodecContext->sample_rate,
                                AV_ROUND_UP
                        ));
                    }

                    if ((format.audioBufferSize = av_samples_get_buffer_size(
                            nullptr,
                            static_cast<int>(format.channels),
                            nbSamples,
                            targetSampleFormat,
                            1
                    )) <= 0) {
                        throw DecoderException(
                                "Could not get audio buffer size, error: " + std::to_string(format.audioBufferSize)
                        );
                    }

                    format.audioBufferSize += AV_INPUT_BUFFER_PADDING_SIZE;
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
                            break;
                        }
                    }
                }

                if (avcodec_open2(videoCodecContext.get(), videoDecoder, nullptr) < 0) {
                    throw DecoderException("Could not open video decoder");
                }

                format.width = videoCodecContext->width;

                format.height = videoCodecContext->height;

                format.frameRate = av_q2d(videoStream->avg_frame_rate);

                if (!audioStream && videoStream->nb_frames <= 1) {
                    format.durationMicros = 0;
                }

                format.videoBufferSize += AV_INPUT_BUFFER_PADDING_SIZE;

                if (decodeVideoStream) {
                    if (width > 0) {
                        format.width = width;
                    }

                    if (height > 0) {
                        format.height = height;
                    }

                    if (frameRate > .0) {
                        format.frameRate = frameRate;
                    }

                    if ((format.videoBufferSize = av_image_get_buffer_size(
                            targetPixelFormat,
                            static_cast<int>(format.width),
                            static_cast<int>(format.height),
                            32
                    )) <= 0) {
                        throw DecoderException(
                                "Could not get video buffer size, error: " + std::to_string(format.videoBufferSize)
                        );
                    }

                    swsContext = std::unique_ptr<SwsContext, SwsContextDeleter>(
                            sws_getContext(
                                    videoCodecContext->width,
                                    videoCodecContext->height,
                                    videoCodecContext->pix_fmt,
                                    static_cast<int>(format.width),
                                    static_cast<int>(format.height),
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
}

std::unique_ptr<Frame> Decoder::decode(uint8_t *buffer, const uint32_t bufferSize) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (_hasAudio() && bufferSize < format.audioBufferSize) {
        throw DecoderException("Wrong audio buffer size");
    }

    if (_hasVideo() && bufferSize < format.videoBufferSize) {
        throw DecoderException("Wrong video buffer size");
    }

    if (!_hasAudio() && !_hasVideo()) {
        return nullptr;
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) == 0) {
            if (_hasAudio() && packet->stream_index == audioStream->index) {
                if (avcodec_send_packet(audioCodecContext.get(), packet.get()) < 0) {
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

                    uint32_t writtenBytes;

                    try {
                        _processAudioFrame(buffer, bufferSize);
                    } catch (...) {
                        av_frame_unref(audioFrame.get());

                        throw;
                    }

                    av_frame_unref(audioFrame.get());

                    return std::make_unique<Frame>(
                            Frame::Type::AUDIO,
                            timestampMicros,
                            writtenBytes
                    );
                }

                if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    throw DecoderException("Error while receiving audio frame");
                }
            } else if (_hasVideo() && packet->stream_index == videoStream->index) {
                if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
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
                        if (av_hwframe_transfer_data(swVideoFrame.get(), hwVideoFrame.get(), 0) < 0) {
                            av_frame_unref(hwVideoFrame.get());

                            throw DecoderException("Error transferring frame to system memory");
                        }

                        swVideoFrame->best_effort_timestamp = hwVideoFrame->best_effort_timestamp;

                        av_frame_unref(hwVideoFrame.get());
                    }

                    const auto frameTimestampMicros = swVideoFrame->best_effort_timestamp;

                    uint32_t writtenBytes;

                    try {
                        writtenBytes = _processVideoFrame(buffer, bufferSize);
                    } catch (...) {
                        av_frame_unref(swVideoFrame.get());

                        throw;
                    }

                    av_frame_unref(swVideoFrame.get());

                    const auto timestampMicros = frameTimestampMicros == AV_NOPTS_VALUE ? 0 : av_rescale_q(
                            frameTimestampMicros,
                            videoStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    return std::make_unique<Frame>(
                            Frame::Type::VIDEO,
                            timestampMicros,
                            writtenBytes
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

        if (audioFrame) {
            av_frame_unref(audioFrame.get());
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

void Decoder::seekTo(const long timestampMicros, const bool keyframesOnly) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (format.durationMicros == 0 || !_hasAudio() && !_hasVideo()) {
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