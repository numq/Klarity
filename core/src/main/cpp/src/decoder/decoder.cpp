#include "decoder.h"

AVCodecContext *Decoder::_initCodecContext(unsigned int streamIndex) {
    auto codec = avcodec_find_decoder(formatContext->streams[streamIndex]->codecpar->codec_id);
    if (!codec) {
        throw DecoderException("Codec not found");
    }

    AVCodecContext *codecContext = avcodec_alloc_context3(codec);
    if (!codecContext) {
        throw DecoderException("Could not allocate codec context");
    }

    if (avcodec_parameters_to_context(codecContext, formatContext->streams[streamIndex]->codecpar) < 0) {
        avcodec_free_context(&codecContext);
        throw DecoderException("Could not copy codec parameters to context");
    }

    if (avcodec_open2(codecContext, codec, nullptr) < 0) {
        avcodec_free_context(&codecContext);
        throw DecoderException("Could not open codec");
    }

    return codecContext;
}

std::vector<uint8_t> &Decoder::_processAudioFrame(const AVFrame &src) {
    if (format.sampleRate <= 0 || format.channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    if (src.format != sampleFormat) {
        auto output_samples = av_rescale_rnd(
                swr_get_delay(swrContext, src.sample_rate) + src.nb_samples,
                src.sample_rate, src.sample_rate, AV_ROUND_UP
        );

        int requiredSize = static_cast<int>(output_samples) * src.ch_layout.nb_channels * sizeof(float);

        if (audioBuffer.size() < requiredSize) {
            audioBuffer.resize(requiredSize);
        }

        auto dataPtr = audioBuffer.data();

        int converted_samples = swr_convert(
                swrContext,
                &dataPtr,
                static_cast<int>(output_samples),
                const_cast<const uint8_t **>(src.data),
                src.nb_samples
        );

        if (converted_samples < 0) {
            throw DecoderException("Error while converting the audio frame");
        }

        audioBuffer.resize(converted_samples * src.ch_layout.nb_channels * sizeof(float));

        return audioBuffer;
    } else {
        auto requiredSize = src.nb_samples * src.ch_layout.nb_channels * sizeof(float);

        if (audioBuffer.size() < requiredSize) {
            audioBuffer.resize(requiredSize);
        }

        memcpy(audioBuffer.data(), src.data[0], requiredSize);

        return audioBuffer;
    }
}

std::vector<uint8_t> &Decoder::_processVideoFrame(const AVFrame &src, int64_t width, int64_t height) {
    if (format.width <= 0 || format.height <= 0) {
        throw DecoderException("Invalid video format");
    }

    auto dstWidth = static_cast<int>(width > 0 && width <= format.width ? width : format.width);

    auto dstHeight = static_cast<int>(height > 0 && height <= format.height ? height : format.height);

    int bufferSize = av_image_get_buffer_size(pixelFormat, dstWidth, dstHeight, 1);
    if (bufferSize < 0) {
        throw DecoderException("Could not get buffer size, error: " + std::to_string(bufferSize));
    }

    if (videoBuffer.size() < bufferSize + AV_INPUT_BUFFER_PADDING_SIZE) {
        videoBuffer.resize(bufferSize + AV_INPUT_BUFFER_PADDING_SIZE);
    }

    if (src.width != dstWidth || src.height != dstHeight || src.format != pixelFormat) {
        int dstLinesize[3];
        av_image_fill_linesizes(dstLinesize, pixelFormat, dstWidth);

        swsContext = sws_getCachedContext(
                swsContext,
                src.width, src.height, static_cast<AVPixelFormat>(src.format),
                dstWidth, dstHeight, pixelFormat,
                SWS_BILINEAR,
                nullptr,
                nullptr,
                nullptr
        );

        if (!swsContext) {
            throw DecoderException("Could not initialize sws context");
        }

        uint8_t *dst[3] = {nullptr};
        av_image_fill_pointers(dst, pixelFormat, dstHeight, videoBuffer.data(), dstLinesize);

        if (sws_scale(swsContext, src.data, src.linesize, 0, src.height, dst, dstLinesize) < 0) {
            throw DecoderException("Error while converting the video frame");
        }
    } else {
        memcpy(videoBuffer.data(), src.data[0], src.linesize[0] * src.height);
    }

    return videoBuffer;
}

void Decoder::_cleanUp() {
    if (swsContext) {
        sws_freeContext(swsContext);
        swsContext = nullptr;
    }

    if (swrContext) {
        swr_free(&swrContext);
    }

    if (audioCodecContext) {
        avcodec_free_context(&audioCodecContext);
    }

    if (videoCodecContext) {
        avcodec_free_context(&videoCodecContext);
    }

    if (formatContext) {
        avformat_close_input(&formatContext);
    }
}

Decoder::Decoder(const std::string &location, bool findAudioStream, bool findVideoStream) {
    av_log_set_level(AV_LOG_QUIET);

    formatContext = avformat_alloc_context();
    if (!formatContext) {
        throw DecoderException("Could not allocate format context");
    }

    int ret = avformat_open_input(&formatContext, location.c_str(), nullptr, nullptr);
    if (ret < 0) {
        _cleanUp();
        char errBuf[128];
        av_strerror(ret, errBuf, sizeof(errBuf));
        std::string errorMsg = "Couldn't open input stream: " + location + " (" + errBuf + ")";
        throw DecoderException(errorMsg);
    }

    if (avformat_find_stream_info(formatContext, nullptr) < 0) {
        _cleanUp();
        throw DecoderException("Couldn't find stream information");
    }

    format = Format{
            location,
            static_cast<uint64_t>(av_rescale_q(formatContext->duration, AV_TIME_BASE_Q, AVRational{1, 1000000}))
    };

    if (findAudioStream) {
        for (unsigned i = 0; i < formatContext->nb_streams; i++) {
            if (formatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
                audioStreamIndex = static_cast<int>(i);

                audioCodecContext = _initCodecContext(audioStreamIndex);

                audioCodecContext->thread_count = 0;

                format.sampleRate = audioCodecContext->sample_rate;

                format.channels = audioCodecContext->ch_layout.nb_channels;

                swrContext = swr_alloc();

                if (!swrContext) {
                    throw DecoderException("Could not allocate swr context");
                }

                if (
                        swr_alloc_set_opts2(
                                &swrContext,
                                &audioCodecContext->ch_layout,
                                sampleFormat,
                                audioCodecContext->sample_rate,
                                &audioCodecContext->ch_layout,
                                audioCodecContext->sample_fmt,
                                audioCodecContext->sample_rate,
                                0,
                                nullptr) < 0
                        ) {
                    _cleanUp();
                    throw DecoderException("Could not set swr context options");
                }

                if (swr_init(swrContext) < 0) {
                    _cleanUp();
                    throw DecoderException("Could not initialize swr context");
                }

                break;
            }
        }
    }

    if (findVideoStream) {
        for (unsigned i = 0; i < formatContext->nb_streams; i++) {
            if (formatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
                videoStreamIndex = static_cast<int>(i);

                videoCodecContext = _initCodecContext(videoStreamIndex);

                videoCodecContext->thread_count = 0;

                format.width = videoCodecContext->width;

                format.height = videoCodecContext->height;

                const auto rational = formatContext->streams[i]->avg_frame_rate;

                format.frameRate = rational.den > 0 ? static_cast<double>(rational.num) / rational.den : 0.0;

                swsContext = sws_alloc_context();

                if (!swsContext) {
                    throw DecoderException("Could not allocate sws context");
                }

                swsContext = sws_getCachedContext(
                        nullptr,
                        videoCodecContext->width, videoCodecContext->height, videoCodecContext->pix_fmt,
                        videoCodecContext->width, videoCodecContext->height, pixelFormat,
                        SWS_BILINEAR,
                        nullptr,
                        nullptr,
                        nullptr
                );

                if (!swsContext) {
                    throw DecoderException("Could not initialize sws context");
                }

                break;
            }
        }
    }
}

Decoder::~Decoder() {
    _cleanUp();
}

std::optional<Frame> Decoder::nextFrame(int64_t width, int64_t height) {
    std::unique_lock<std::mutex> lock(mutex);

    AVPacketGuard packetGuard;

    AVFrameGuard frameGuard;

    while (av_read_frame(formatContext, packetGuard.get()) == 0) {
        if (audioCodecContext && audioStreamIndex != -1 && packetGuard.get()->stream_index == audioStreamIndex) {
            avcodec_send_packet(audioCodecContext, packetGuard.get());
            if (avcodec_receive_frame(audioCodecContext, frameGuard.get()) == 0) {
                auto data = _processAudioFrame(*frameGuard.get());
                const auto timestampMicros = static_cast<int64_t>(std::round(
                        static_cast<double>(frameGuard.get()->best_effort_timestamp) *
                        av_q2d(formatContext->streams[audioStreamIndex]->time_base) * 1000000));
                return Frame{Frame::AUDIO, timestampMicros, data};
            }
            av_frame_unref(frameGuard.get());
        }

        if (videoCodecContext && videoStreamIndex != -1 && packetGuard.get()->stream_index == videoStreamIndex) {
            avcodec_send_packet(videoCodecContext, packetGuard.get());
            if (avcodec_receive_frame(videoCodecContext, frameGuard.get()) == 0) {
                auto data = _processVideoFrame(*frameGuard.get(), width, height);
                const auto timestampMicros = static_cast<int64_t>(std::round(
                        static_cast<double>(frameGuard.get()->best_effort_timestamp) *
                        av_q2d(formatContext->streams[videoStreamIndex]->time_base) * 1000000));
                return Frame{Frame::VIDEO, timestampMicros, data};
            } else {
                av_frame_unref(frameGuard.get());
            }
        }

        av_packet_unref(packetGuard.get());
    }

    return std::nullopt;
}

void Decoder::seekTo(long timestampMicros, bool keyframesOnly) {
    std::unique_lock<std::mutex> lock(mutex);

    if (!audioCodecContext && !videoCodecContext) {
        return;
    }

    if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    int ret = av_seek_frame(formatContext, -1, timestampMicros, AVSEEK_FLAG_BACKWARD);
    if (ret < 0) {
        throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext);
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext);
    }

    if (!keyframesOnly) {
        AVPacketGuard packetGuard;

        AVFrameGuard frameGuard;

        bool found = false;

        int64_t maxFrames = 0;

        int64_t audioTimestamp = (audioStreamIndex != -1)
                                 ? av_rescale_q(timestampMicros, AV_TIME_BASE_Q,
                                                formatContext->streams[audioStreamIndex]->time_base) : 0;
        int64_t videoTimestamp = (videoStreamIndex != -1)
                                 ? av_rescale_q(timestampMicros, AV_TIME_BASE_Q,
                                                formatContext->streams[videoStreamIndex]->time_base) : 0;

        if (audioCodecContext && audioStreamIndex != -1) {
            maxFrames = std::max(static_cast<int64_t>(
                                         static_cast<double>(timestampMicros) /
                                         (static_cast<double>(format.durationMicros) /
                                          static_cast<double>(formatContext->streams[audioStreamIndex]->nb_frames))
                                 ), maxFrames);
        }

        if (videoCodecContext && videoStreamIndex != -1) {
            maxFrames = std::max(static_cast<int64_t>(
                                         static_cast<double>(timestampMicros) /
                                         (static_cast<double>(format.durationMicros) /
                                          static_cast<double>(formatContext->streams[videoStreamIndex]->nb_frames))
                                 ), maxFrames);
        }

        while (maxFrames-- > 0 && av_read_frame(formatContext, packetGuard.get()) >= 0) {
            if (audioCodecContext && audioStreamIndex != -1 && packetGuard.get()->stream_index == audioStreamIndex) {
                avcodec_send_packet(audioCodecContext, packetGuard.get());

                while (avcodec_receive_frame(audioCodecContext, frameGuard.get()) == 0) {
                    if (frameGuard.get()->best_effort_timestamp >= audioTimestamp) {
                        found = true;
                        break;
                    }
                }
            }

            if (videoCodecContext && videoStreamIndex != -1 && packetGuard.get()->stream_index == videoStreamIndex) {
                avcodec_send_packet(videoCodecContext, packetGuard.get());

                while (avcodec_receive_frame(videoCodecContext, frameGuard.get()) == 0) {
                    if (frameGuard.get()->best_effort_timestamp >= videoTimestamp) {
                        found = true;
                        break;
                    }
                }
            }

            av_packet_unref(packetGuard.get());

            if (found) break;
        }
    }
}

void Decoder::reset() {
    std::unique_lock<std::mutex> lock(mutex);

    if (!audioCodecContext && !videoCodecContext) {
        return;
    }

    if (av_seek_frame(formatContext, -1, 0, AVSEEK_FLAG_FRAME) < 0) {
        throw DecoderException("Error resetting stream");
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext);
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext);
    }
}