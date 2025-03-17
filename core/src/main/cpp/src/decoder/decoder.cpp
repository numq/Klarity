#include "decoder.h"

AVCodecContext *Decoder::_initializeCodecContext(AVCodecParameters *avCodecParameters) {
    auto codec = avcodec_find_decoder(avCodecParameters->codec_id);
    if (!codec) {
        throw DecoderException("Codec not found");
    }

    auto codecContext = avcodec_alloc_context3(codec);
    if (!codecContext) {
        throw DecoderException("Could not allocate codec context");
    }

    if (avcodec_parameters_to_context(codecContext, avCodecParameters) < 0) {
        avcodec_free_context(&codecContext);
        throw DecoderException("Could not copy codec parameters to context");
    }

    if (avcodec_open2(codecContext, codec, nullptr) < 0) {
        avcodec_free_context(&codecContext);
        throw DecoderException("Could not open codec");
    }

    return codecContext;
}

void Decoder::_processAudioFrame(const AVFrame &src) {
    if (format.sampleRate <= 0 || format.channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    if (src.format != sampleFormat) {
        auto output_samples = av_rescale_rnd(
                swr_get_delay(swrContext, src.sample_rate) + src.nb_samples,
                src.sample_rate, src.sample_rate, AV_ROUND_UP
        );

        int requiredSize = static_cast<int>(output_samples) * src.ch_layout.nb_channels * sizeof(float);

        audioBuffer.resize(requiredSize);

        auto bufferPtr = audioBuffer.data();

        int converted_samples = swr_convert(
                swrContext,
                &bufferPtr,
                static_cast<int>(output_samples),
                const_cast<const uint8_t **>(src.data),
                src.nb_samples
        );

        if (converted_samples < 0) {
            throw DecoderException("Error while converting the audio frame");
        }
    } else {
        auto requiredSize = src.nb_samples * src.ch_layout.nb_channels * sizeof(float);
        auto data = src.data[0];

        audioBuffer.resize(requiredSize);

        memcpy(audioBuffer.data(), data, requiredSize);
    }
}

void Decoder::_processVideoFrame(const AVFrame &src, int dstWidth, int dstHeight) {
    if (format.width <= 0 || format.height <= 0) {
        throw DecoderException("Invalid video format");
    }

    if (static_cast<AVPixelFormat>(src.format) == AV_PIX_FMT_NONE) {
        throw DecoderException("Invalid pixel format");
    }

    if (dstWidth <= 0 || dstHeight <= 0) {
        throw DecoderException("Invalid destination dimensions");
    }

    auto srcFormat = src.format;

    switch (src.format) {
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

    if (srcFormat != pixelFormat || src.width != dstWidth || src.height != dstHeight) {
        if (swsPixelFormat != pixelFormat || swsWidth != dstWidth || swsHeight != dstHeight) {
            swsContext = sws_getCachedContext(
                    swsContext,
                    src.width, src.height, static_cast<AVPixelFormat>(srcFormat),
                    dstWidth, dstHeight, pixelFormat,
                    SWS_BILINEAR,
                    nullptr, nullptr, nullptr
            );

            if (swsContext) {
                swsPixelFormat = pixelFormat;

                swsWidth = dstWidth;

                swsHeight = dstHeight;
            } else {
                swsPixelFormat = AV_PIX_FMT_NONE;

                swsWidth = -1;

                swsHeight = -1;
            }
        }

        if (!swsContext || swsPixelFormat == AV_PIX_FMT_NONE || swsWidth == -1 || swsHeight == -1) {
            throw DecoderException("Could not initialize sws context");
        }

        int bufferSize = av_image_get_buffer_size(static_cast<AVPixelFormat>(swsPixelFormat), swsWidth, swsHeight, 1);
        if (bufferSize < 0) {
            throw DecoderException("Could not get buffer size, error: " + std::to_string(bufferSize));
        }

        videoBuffer.resize(bufferSize);

        std::vector<int> dstLineSize(AV_NUM_DATA_POINTERS, 0);

        av_image_fill_linesizes(dstLineSize.data(), static_cast<AVPixelFormat>(swsPixelFormat), swsWidth);

        std::vector<uint8_t *> dst(AV_NUM_DATA_POINTERS, nullptr);

        av_image_fill_pointers(
                dst.data(),
                static_cast<AVPixelFormat>(swsPixelFormat),
                swsHeight,
                videoBuffer.data(),
                dstLineSize.data()
        );

        if (sws_scale(
                swsContext,
                src.data,
                src.linesize,
                0,
                src.height,
                dst.data(),
                dstLineSize.data()
        ) < 0) {
            throw DecoderException("Error while converting the video frame");
        }
    } else {
        videoBuffer.resize(src.linesize[0] * src.height);

        memcpy(videoBuffer.data(), src.data[0], src.linesize[0] * src.height);
    }
}

void Decoder::_cleanUp() {
    if (swrContext) {
        swr_free(&swrContext);
    }

    if (swsContext) {
        sws_freeContext(swsContext);
        swsContext = nullptr;
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
    try {
        formatContext = avformat_alloc_context();
        if (!formatContext) {
            throw DecoderException("Could not allocate format context");
        }

        int ret = avformat_open_input(&formatContext, location.c_str(), nullptr, nullptr);
        if (ret < 0) {
            avformat_free_context(formatContext);
            throw DecoderException("Could not open input stream for location: " + location);
        }

        if (avformat_find_stream_info(formatContext, nullptr) < 0) {
            avformat_close_input(&formatContext);
            throw DecoderException("Could not find stream information");
        }

        format = Format{
                location,
                static_cast<uint64_t>(
                        av_rescale_q(formatContext->duration, AV_TIME_BASE_Q, AVRational{1, 1'000'000})
                )
        };

        for (int streamIndex = 0; streamIndex < formatContext->nb_streams; streamIndex++) {
            auto avCodecParameters = formatContext->streams[streamIndex]->codecpar;

            if (avCodecParameters) {
                if (findAudioStream && avCodecParameters->codec_type == AVMEDIA_TYPE_AUDIO) {
                    audioCodecContext = _initializeCodecContext(avCodecParameters);

                    audioStreamIndex = streamIndex;

                    if (swr_alloc_set_opts2(
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
                        throw DecoderException("Could not allocate swr context");
                    }

                    if (swr_init(swrContext) < 0) {
                        swr_free(&swrContext);
                        throw DecoderException("Could not initialize swr context");
                    }

                    format.sampleRate = audioCodecContext->sample_rate;

                    format.channels = audioCodecContext->ch_layout.nb_channels;
                }

                if (findVideoStream && avCodecParameters->codec_type == AVMEDIA_TYPE_VIDEO) {
                    videoCodecContext = _initializeCodecContext(avCodecParameters);

                    videoStreamIndex = streamIndex;

                    swsContext = sws_getContext(
                            videoCodecContext->width, videoCodecContext->height, videoCodecContext->pix_fmt,
                            videoCodecContext->width, videoCodecContext->height, pixelFormat,
                            SWS_BILINEAR,
                            nullptr, nullptr, nullptr
                    );

                    if (!swsContext) {
                        throw DecoderException("Could not allocate sws context");
                    }

                    swsPixelFormat = videoCodecContext->pix_fmt;

                    swsWidth = videoCodecContext->width;

                    swsHeight = videoCodecContext->height;

                    format.width = videoCodecContext->width;

                    format.height = videoCodecContext->height;

                    const auto rational = formatContext->streams[streamIndex]->avg_frame_rate;

                    format.frameRate = rational.den > 0 ? static_cast<double>(rational.num) / rational.den : 0.0;
                }
            }
        }
    } catch (...) {
        _cleanUp();
        throw;
    }
}

Decoder::~Decoder() {
    _cleanUp();
}

std::optional<Frame> Decoder::nextFrame(int width, int height) {
    std::unique_lock<std::mutex> lock(mutex);

    if (audioStreamIndex == -1 && videoStreamIndex == -1) {
        return std::nullopt;
    }

    AVPacketGuard packetGuard;

    AVFrameGuard frameGuard;

    while (av_read_frame(formatContext, packetGuard.get()) == 0) {
        if (audioCodecContext && audioStreamIndex != -1 && packetGuard.get()->stream_index == audioStreamIndex) {
            if (avcodec_send_packet(audioCodecContext, packetGuard.get()) < 0) {
                continue;
            }

            auto ret = avcodec_receive_frame(audioCodecContext, frameGuard.get());
            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                continue;
            } else if (ret < 0) {
                throw DecoderException("Error while receiving audio frame.");
            }

            _processAudioFrame(*frameGuard.get());

            auto pts = frameGuard.get()->pts != AV_NOPTS_VALUE ? frameGuard.get()->pts
                                                               : frameGuard.get()->best_effort_timestamp;

            const auto timestampMicros = static_cast<int64_t>(std::round(
                    static_cast<double>(pts) * av_q2d(formatContext->streams[audioStreamIndex]->time_base) *
                    1'000'000));

            av_frame_unref(frameGuard.get());

            return Frame{Frame::AUDIO, timestampMicros, audioBuffer};
        } else if (packetGuard.get()->stream_index == videoStreamIndex) {
            if (avcodec_send_packet(videoCodecContext, packetGuard.get()) < 0) {
                continue;
            }

            auto ret = avcodec_receive_frame(videoCodecContext, frameGuard.get());
            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                continue;
            } else if (ret < 0) {
                throw DecoderException("Error while receiving video frame.");
            }

            _processVideoFrame(*frameGuard.get(), width, height);

            auto pts = frameGuard.get()->pts != AV_NOPTS_VALUE ? frameGuard.get()->pts
                                                               : frameGuard.get()->best_effort_timestamp;

            const auto timestampMicros = static_cast<int64_t>(std::round(
                    static_cast<double>(pts) * av_q2d(formatContext->streams[videoStreamIndex]->time_base) *
                    1'000'000));

            av_frame_unref(frameGuard.get());

            return Frame{Frame::VIDEO, timestampMicros, videoBuffer};
        }

        av_packet_unref(packetGuard.get());
    }

    return std::nullopt;
}

void Decoder::seekTo(long timestampMicros, bool keyframesOnly) {
    std::unique_lock<std::mutex> lock(mutex);

    if (audioStreamIndex == -1 && videoStreamIndex == -1) {
        return;
    }

    if (format.durationMicros > 0) {
        if (audioCodecContext) {
            avcodec_flush_buffers(audioCodecContext);
        }

        if (videoCodecContext) {
            avcodec_flush_buffers(videoCodecContext);
        }

        if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
            throw DecoderException("Timestamp out of bounds");
        }

        auto streamIndex = videoStreamIndex != -1 ? videoStreamIndex : audioStreamIndex;

        if (av_seek_frame(
                formatContext,
                streamIndex,
                timestampMicros,
                keyframesOnly ? AVSEEK_FLAG_BACKWARD : AVSEEK_FLAG_ANY
        ) < 0) {
            throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
        }
    }
}

void Decoder::reset() {
    std::unique_lock<std::mutex> lock(mutex);

    if (audioStreamIndex == -1 && videoStreamIndex == -1) {
        return;
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext);
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext);
    }

    auto streamIndex = videoStreamIndex != -1 ? videoStreamIndex : audioStreamIndex;

    if (av_seek_frame(formatContext, streamIndex, 0, AVSEEK_FLAG_BACKWARD) < 0) {
        throw DecoderException("Error resetting stream");
    }
}