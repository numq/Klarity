#include "decoder.h"

AVPixelFormat Decoder::_getHardwareAccelerationFormat(AVCodecContext *codecContext, const AVPixelFormat *pixelFormats) {
    const AVPixelFormat *pixelFormat;

    auto selectedPixelFormat = static_cast<AVPixelFormat>(reinterpret_cast<intptr_t>(codecContext->opaque));

    for (pixelFormat = pixelFormats; *pixelFormat != AVPixelFormat::AV_PIX_FMT_NONE; pixelFormat++) {
        if (*pixelFormat == selectedPixelFormat) {
            return *pixelFormat;
        }
    }

    return AVPixelFormat::AV_PIX_FMT_NONE;
}

bool Decoder::_hasAudio() {
    if (!audioCodecContext ||
        !audioStream ||
        !audioStream->codecpar ||
        !audioDecoder ||
        !audioFrame ||
        !audioFramePool) {
        return false;
    }

    return format.sampleRate > 0 && format.channels > 0;
}

bool Decoder::_hasVideo() {
    if (!videoCodecContext ||
        !videoStream ||
        !videoStream->codecpar ||
        !videoDecoder ||
        !swVideoFrame ||
        !videoFramePool) {
        return false;
    }

    return format.width > 0 && format.height > 0 && videoCodecContext->pix_fmt != AVPixelFormat::AV_PIX_FMT_NONE;
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
    if (deviceType == AVHWDeviceType::AV_HWDEVICE_TYPE_NONE) {
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

void Decoder::_processAudioFrame(AVFrame *dst) {
    if (!dst) {
        throw DecoderException("Invalid destination frame");
    }

    if (format.sampleRate <= 0 || format.channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    if (!audioFrame || !swrContext) {
        throw DecoderException("Invalid audio frame or conversion context");
    }

    auto src = audioFrame.get();

    if (src->format == AV_SAMPLE_FMT_NONE) {
        throw DecoderException("Invalid source sample format");
    }

    const auto nbSamples = av_rescale_rnd(
            swr_get_delay(swrContext.get(), src->sample_rate) + src->nb_samples,
            format.sampleRate,
            src->sample_rate,
            AV_ROUND_UP
    );

    dst->format = targetSampleFormat;

    dst->nb_samples = static_cast<int>(nbSamples);

    av_channel_layout_copy(&dst->ch_layout, &targetChannelLayout);

    if (av_frame_get_buffer(dst, 0) < 0) {
        throw DecoderException("Could not allocate audio frame buffers");
    }

    if (src->format != targetSampleFormat) {
        swr_convert(
                swrContext.get(),
                dst->data,
                static_cast<int>(nbSamples),
                const_cast<const uint8_t **>(src->data),
                src->nb_samples
        );
    } else {
        if (av_sample_fmt_is_planar(targetSampleFormat)) {
            for (int ch = 0; ch < src->ch_layout.nb_channels; ch++) {
                if (!src->data[ch]) {
                    throw DecoderException("Invalid source audio data");
                }

                int channelSize;

                if ((channelSize = av_samples_get_buffer_size(
                        nullptr,
                        1,
                        static_cast<int>(nbSamples),
                        targetSampleFormat, 1)
                    ) < 0) {
                    throw DecoderException("Could not get per-channel buffer size");
                }

                memcpy(dst->data[ch], src->data[ch], channelSize);
            }
        } else {
            if (!src->data[0]) {
                throw DecoderException("Invalid source audio data");
            }

            int bufferSize;

            if ((bufferSize = av_samples_get_buffer_size(
                    nullptr,
                    src->ch_layout.nb_channels,
                    static_cast<int>(nbSamples),
                    targetSampleFormat,
                    1
            )) < 0) {
                throw DecoderException("Could not get buffer size");
            }

            memcpy(dst->data[0], src->data[0], bufferSize);
        }
    }
}

void Decoder::_processVideoFrame(AVFrame *dst) {
    if (!dst) {
        throw DecoderException("Invalid destination frame");
    }

    if (format.width <= 0 || format.height <= 0) {
        throw DecoderException("Invalid video format");
    }

    if (!swVideoFrame || !swsContext) {
        throw DecoderException("Invalid video frame or conversion context");
    }

    auto src = swVideoFrame.get();

    if (src->format == AVPixelFormat::AV_PIX_FMT_NONE) {
        throw DecoderException("Invalid source pixel format");
    }

    dst->format = targetPixelFormat;

    dst->width = targetWidth;

    dst->height = targetHeight;

    if (!dst->data[0] && av_frame_get_buffer(dst, 0) < 0) {
        throw DecoderException("Could not allocate video frame buffers");
    }

    if (sws_scale_frame(swsContext.get(), dst, src) < 0) {
        throw DecoderException("Error while converting the video frame");
    }
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
            formatContext->duration == AV_NOPTS_VALUE ? 0 : formatContext->duration
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
                    audioCodecContext->thread_count = 0;

                    audioCodecContext->thread_type = FF_THREAD_FRAME;

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

                    audioFramePool = std::make_unique<FramePool>(audioFramePoolCapacity);

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

                format.frameRate = av_q2d(videoStream->avg_frame_rate);

                if (decodeVideoStream) {
                    videoCodecContext->thread_count = 0;

                    videoCodecContext->thread_type = FF_THREAD_FRAME | FF_THREAD_SLICE;

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

                    swsPixelFormat = static_cast<AVPixelFormat>(videoCodecContext->pix_fmt);

                    swsWidth = videoCodecContext->width;

                    swsHeight = videoCodecContext->height;

                    av_opt_set(swsContext.get(), "threads", "auto", 0);

                    swVideoFrame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

                    if (!swVideoFrame) {
                        throw DecoderException("Memory allocation failed for software video frame");
                    }

                    videoFramePool = std::make_unique<FramePool>(videoFramePoolCapacity);
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

    videoFramePool.reset();

    audioFramePool.reset();

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

    auto frame = audioFramePool->acquire();

    if (!frame) {
        throw DecoderException("Could not get audio frame from pool");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) == 0) {
            if (packet->stream_index == audioStream->index) {
                if (avcodec_send_packet(audioCodecContext.get(), packet.get()) < 0) {
                    continue;
                }

                av_packet_unref(packet.get());

                int ret;

                while ((ret = avcodec_receive_frame(audioCodecContext.get(), audioFrame.get())) == 0) {
                    const auto frameTimestampMicros = audioFrame->best_effort_timestamp
                                                      ? audioFrame->best_effort_timestamp : audioFrame->pts;

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            audioStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    _processAudioFrame(frame.get());

                    av_frame_unref(audioFrame.get());

                    auto buffer = frame->data[0];

                    int size = av_samples_get_buffer_size(
                            nullptr,
                            frame->ch_layout.nb_channels,
                            frame->nb_samples,
                            static_cast<AVSampleFormat>(frame->format),
                            1
                    );

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

    auto frame = videoFramePool->acquire();

    if (!frame) {
        throw DecoderException("Could not get video frame from pool");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) == 0) {
            if (_hasVideo() && packet->stream_index == videoStream->index) {
                if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
                    continue;
                }

                av_packet_unref(packet.get());

                int ret;

                while ((ret = avcodec_receive_frame(
                        videoCodecContext.get(),
                        _isHardwareAccelerated() ? hwVideoFrame.get() : swVideoFrame.get()
                )) == 0) {
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

                    const auto frameTimestampMicros = swVideoFrame->best_effort_timestamp
                                                      ? swVideoFrame->best_effort_timestamp : swVideoFrame->pts;

                    if (swVideoFrame->width != swsWidth ||
                        swVideoFrame->height != swsHeight ||
                        swVideoFrame->format != swsPixelFormat) {
                        swsContext.reset(sws_getCachedContext(
                                swsContext.release(),
                                swVideoFrame->width,
                                swVideoFrame->height,
                                static_cast<AVPixelFormat>(swVideoFrame->format),
                                targetWidth,
                                targetHeight,
                                targetPixelFormat,
                                swsFlags,
                                nullptr,
                                nullptr,
                                nullptr
                        ));

                        swsPixelFormat = static_cast<AVPixelFormat>(swVideoFrame->format);

                        swsWidth = swVideoFrame->width;

                        swsHeight = swVideoFrame->height;

                        av_opt_set(swsContext.get(), "threads", "auto", 0);
                    }

                    _processVideoFrame(frame.get());

                    av_frame_unref(swVideoFrame.get());

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            videoStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    auto buffer = frame->data[0];

                    auto size = frame->linesize[0] * frame->height;

                    return std::make_unique<Frame>(
                            buffer,
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

/*std::unique_ptr<Frame> Decoder::decodeMedia() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (!_hasAudio() && !_hasVideo()) {
        throw DecoderException("Unable to find media stream");
    }

    av_packet_unref(packet.get());

    try {
        while (av_read_frame(formatContext.get(), packet.get()) == 0) {
            if (packet->stream_index == audioStream->index) {
                if (avcodec_send_packet(audioCodecContext.get(), packet.get()) < 0) {
                    continue;
                }

                av_packet_unref(packet.get());

                int ret;

                while ((ret = avcodec_receive_frame(audioCodecContext.get(), audioFrame.get())) == 0) {
                    const auto frameTimestampMicros = audioFrame->best_effort_timestamp
                                                      ? audioFrame->best_effort_timestamp : audioFrame->pts;

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            audioStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    _processAudioFrame();

                    av_frame_unref(audioFrame.get());

                    auto size = audioBuffer.size();

                    auto buffer = malloc(size);

                    if (!buffer) {
                        throw DecoderException("Audio frame buffer allocation error");
                    }

                    memcpy(buffer, audioBuffer.data(), size);

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
            } else {
                if (avcodec_send_packet(videoCodecContext.get(), packet.get()) < 0) {
                    continue;
                }

                av_packet_unref(packet.get());

                int ret;

                while ((ret = avcodec_receive_frame(
                        videoCodecContext.get(),
                        _isHardwareAccelerated() ? hwVideoFrame.get() : swVideoFrame.get()
                )) == 0) {
                    if (_isHardwareAccelerated()) {
                        if (av_hwframe_transfer_data(swVideoFrame.get(), hwVideoFrame.get(), 0) < 0) {
                            throw DecoderException("Error transferring frame to system memory");
                        }

                        if (swVideoFrame->format == AV_PIX_FMT_NONE || !swVideoFrame->data[0]) {
                            throw DecoderException("Failed to transfer frame data");
                        }

                        swVideoFrame->best_effort_timestamp = hwVideoFrame->best_effort_timestamp;

                        swVideoFrame->pts = hwVideoFrame->pts;

                        av_frame_unref(hwVideoFrame.get());
                    }

                    const auto frameTimestampMicros = swVideoFrame->best_effort_timestamp
                                                      ? swVideoFrame->best_effort_timestamp : swVideoFrame->pts;

                    if (swVideoFrame->width != swsWidth ||
                        swVideoFrame->height != swsHeight ||
                        swVideoFrame->format != swsPixelFormat) {
                        swsContext.reset(sws_getCachedContext(
                                swsContext.release(),
                                swVideoFrame->width,
                                swVideoFrame->height,
                                static_cast<AVPixelFormat>(swVideoFrame->format),
                                static_cast<int>(format.width),
                                static_cast<int>(format.height),
                                targetPixelFormat,
                                swsFlags,
                                nullptr,
                                nullptr,
                                nullptr
                        ));

                        swsPixelFormat = static_cast<AVPixelFormat>(swVideoFrame->format);

                        swsWidth = swVideoFrame->width;

                        swsHeight = swVideoFrame->height;

                        av_opt_set(swsContext.get(), "threads", "auto", 0);
                    }

                    _processVideoFrame();

                    av_frame_unref(swVideoFrame.get());

                    const auto timestampMicros = av_rescale_q(
                            frameTimestampMicros,
                            videoStream->time_base,
                            AVRational{1, 1'000'000}
                    );

                    auto size = videoBuffer.size();

                    auto buffer = malloc(size);

                    if (!buffer) {
                        throw DecoderException("Video frame buffer allocation error");
                    }

                    memcpy(buffer, videoBuffer.data(), size);

                    return std::make_unique<Frame>(
                            buffer,
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
}*/

uint64_t Decoder::seekTo(const long timestampMicros, const bool keyframesOnly) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!_isValid()) {
        throw DecoderException("Could not use uninitialized decoder");
    }

    if (format.durationMicros == 0 || (!_hasAudio() && !_hasVideo())) {
        return -1;
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

    if (_hasAudio()) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (_hasVideo()) {
        avcodec_flush_buffers(videoCodecContext.get());
    }

    long actualTimestamp = -1;

    auto frame = av_frame_alloc();

    if (!frame) {
        throw DecoderException("Failed to allocate seeking frame");
    }

    try {
        while (av_read_frame(formatContext.get(), packet.get()) == 0) {
            if (packet->stream_index == streamIndex) {
                auto codecContext = _hasVideo() ? videoCodecContext.get() : audioCodecContext.get();

                if (avcodec_send_packet(codecContext, packet.get()) < 0) {
                    av_packet_unref(packet.get());

                    continue;
                }

                int ret = avcodec_receive_frame(codecContext, frame);

                av_packet_unref(packet.get());

                if (ret >= 0) {
                    int64_t frameTimestamp =
                            frame->best_effort_timestamp != AV_NOPTS_VALUE ? frame->best_effort_timestamp : frame->pts;

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