#include "decoder.h"

std::vector<uint8_t> Decoder::_processAudioFrame(const AVFrame &src) {
    if (format->sampleRate <= 0 || format->channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    if (src.format != AV_SAMPLE_FMT_FLT) {
        const auto chLayout = src.ch_layout;

        swrContext.reset(swr_alloc());

        if (!swrContext) {
            throw DecoderException("Could not allocate the resampling context");
        }

        auto rawSwrContext = swrContext.get();

        if (swr_alloc_set_opts2(
                &rawSwrContext,
                &chLayout,
                AV_SAMPLE_FMT_FLT,
                src.sample_rate,
                &chLayout,
                static_cast<AVSampleFormat>(src.format),
                src.sample_rate,
                0,
                nullptr) < 0) {
            throw DecoderException("Could not set options for the resampling context");
        }

        swrContext.reset(rawSwrContext);

        if (swr_init(swrContext.get()) < 0) {
            throw DecoderException("Could not initialize the resampling context");
        }

        auto output_samples = av_rescale_rnd(
                swr_get_delay(swrContext.get(), src.sample_rate) + src.nb_samples,
                src.sample_rate, src.sample_rate, AV_ROUND_UP);

        std::vector<uint8_t> data(output_samples * src.ch_layout.nb_channels * sizeof(float));

        auto dataPtr = data.data();

        int converted_samples = swr_convert(
                swrContext.get(),
                &dataPtr,
                static_cast<int>(output_samples),
                const_cast<const uint8_t **>(src.data),
                src.nb_samples
        );

        if (converted_samples < 0) {
            throw DecoderException("Error while converting the audio frame");
        }

        data.resize(converted_samples * src.ch_layout.nb_channels * sizeof(float));

        return data;
    }

    std::vector<uint8_t> output(src.nb_samples * src.ch_layout.nb_channels * sizeof(float));

    memcpy(output.data(), src.data[0], src.nb_samples * src.ch_layout.nb_channels * sizeof(float));

    return output;
}

std::vector<uint8_t> Decoder::_processVideoFrame(const AVFrame &src, int64_t width, int64_t height) {
    if (format->width <= 0 || format->height <= 0) {
        throw DecoderException("Invalid video format");
    }

    auto dstWidth = static_cast<int>(width > 0 && width <= format->width ? width : format->width);
    auto dstHeight = static_cast<int>(height > 0 && height <= format->height ? height : format->height);

    auto srcFormat = static_cast<AVPixelFormat>(src.format);
    auto dstFormat = AV_PIX_FMT_RGBA;

    int dstLinesize[3];
    av_image_fill_linesizes(dstLinesize, dstFormat, dstWidth);

    if (src.width != dstWidth || src.height != dstHeight || src.format != dstFormat) {
        swsContext.reset(
                sws_getCachedContext(
                        nullptr,
                        src.width, src.height, srcFormat,
                        dstWidth, dstHeight, dstFormat,
                        SWS_BILINEAR,
                        nullptr,
                        nullptr,
                        nullptr
                )
        );

        if (!swsContext) {
            throw DecoderException("Could not initialize the conversion context");
        }

        int bufferSize = av_image_get_buffer_size(dstFormat, dstWidth, dstHeight, 1);
        if (bufferSize < 0) {
            throw DecoderException("Could not get buffer size, error: " + std::to_string(bufferSize));
        }

        std::vector<uint8_t> output(bufferSize + AV_INPUT_BUFFER_PADDING_SIZE);

        uint8_t *dst[3] = {nullptr};
        av_image_fill_pointers(dst, dstFormat, dstHeight, output.data(), dstLinesize);

        if (sws_scale(swsContext.get(), src.data, src.linesize, 0, src.height, dst, dstLinesize) < 0) {
            throw DecoderException("Error while converting the video frame");
        }

        return output;
    }

    std::vector<uint8_t> output(src.linesize[0] * src.height);

    memcpy(output.data(), src.data[0], src.linesize[0] * src.height);

    return output;
}

Decoder::Decoder(const std::string &location, bool findAudioStream, bool findVideoStream) {
    av_log_set_level(AV_LOG_QUIET);

    formatContext.reset(avformat_alloc_context());

    if (!formatContext) {
        throw DecoderException("Could not allocate format context");
    }

    auto rawFormatContext = formatContext.get();

    if (avformat_open_input(&rawFormatContext, location.c_str(), nullptr, nullptr) < 0) {
        throw DecoderException("Couldn't open input stream: " + location);
    }

    if (avformat_find_stream_info(formatContext.get(), nullptr) < 0) {
        formatContext.reset();
        throw DecoderException("Couldn't find stream information");
    }

    format = std::make_unique<Format>(
            location,
            av_rescale_q(formatContext->duration, AV_TIME_BASE_Q, (AVRational) {1, 1000000})
    );

    if (findAudioStream) {
        for (unsigned i = 0; i < formatContext->nb_streams; i++) {
            if (formatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
                audioStream.reset(formatContext->streams[i]);
                auto codec = avcodec_find_decoder(audioStream->codecpar->codec_id);
                if (!codec) {
                    throw DecoderException("Audio codec not found");
                }

                audioCodecContext.reset(avcodec_alloc_context3(codec));
                if (!audioCodecContext) {
                    throw DecoderException("Could not allocate audio codec context");
                }

                if (avcodec_parameters_to_context(audioCodecContext.get(), audioStream->codecpar) < 0) {
                    audioCodecContext.reset();
                    throw DecoderException("Could not copy audio codec parameters to context");
                }

                if (avcodec_open2(audioCodecContext.get(), codec, nullptr) < 0) {
                    audioCodecContext.reset();
                    throw DecoderException("Could not open audio codec");
                }

                audioCodecContext->thread_count = 0;

                format->sampleRate = audioCodecContext->sample_rate;
                format->channels = audioCodecContext->ch_layout.nb_channels;

                break;
            }
        }
    }

    if (findVideoStream) {
        for (unsigned i = 0; i < formatContext->nb_streams; i++) {
            if (formatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
                videoStream.reset(formatContext->streams[i]);

                auto codec = avcodec_find_decoder(videoStream->codecpar->codec_id);
                if (!codec) {
                    throw DecoderException("Video codec not found");
                }

                videoCodecContext.reset(avcodec_alloc_context3(codec));
                if (!videoCodecContext) {
                    throw DecoderException("Could not allocate video codec context");
                }

                if (avcodec_parameters_to_context(videoCodecContext.get(), videoStream->codecpar) < 0) {
                    videoCodecContext.reset();
                    throw DecoderException("Could not copy video codec parameters to context");
                }

                if (avcodec_open2(videoCodecContext.get(), codec, nullptr) < 0) {
                    videoCodecContext.reset();
                    throw DecoderException("Could not open video codec");
                }

                videoCodecContext->thread_count = 0;

                format->width = videoCodecContext->width;
                format->height = videoCodecContext->height;

                const auto rational = videoStream->avg_frame_rate;
                format->frameRate = rational.den > 0 ? static_cast<double>(rational.num) / rational.den : 0.0;

                break;
            }
        }
    }

    if ((findAudioStream && !audioStream) && (findVideoStream && !videoStream)) {
        throw DecoderException("No valid streams found");
    }
}

std::unique_ptr<Frame> Decoder::nextFrame(int64_t width, int64_t height) {
    std::unique_lock<std::mutex> lock(mutex);

    auto packet = std::unique_ptr<AVPacket, av_packet_deleter>(av_packet_alloc());
    if (!packet) {
        throw DecoderException("Could not allocate AVPacket");
    }

    auto frame = std::unique_ptr<AVFrame, av_frame_deleter>(av_frame_alloc());
    if (!frame) {
        throw DecoderException("Could not allocate AVFrame");
    }

    while (av_read_frame(formatContext.get(), packet.get()) == 0) {
        if (audioStream && audioCodecContext) {
            if (packet->stream_index == audioStream->index) {
                avcodec_send_packet(audioCodecContext.get(), packet.get());
                if (avcodec_receive_frame(audioCodecContext.get(), frame.get()) == 0) {
                    std::vector<uint8_t> data = _processAudioFrame(*frame);
                    const auto timestampMicros = static_cast<int64_t>(
                            std::round(
                                    static_cast<double>(frame->best_effort_timestamp) * av_q2d(audioStream->time_base) *
                                    1000000)
                    );
                    return std::make_unique<Frame>(Frame::AUDIO, timestampMicros, data);
                }

                av_frame_unref(frame.get());
            }
        }

        if (videoStream && videoCodecContext) {
            if (packet->stream_index == videoStream->index) {
                avcodec_send_packet(videoCodecContext.get(), packet.get());
                if (avcodec_receive_frame(videoCodecContext.get(), frame.get()) == 0) {
                    std::vector<uint8_t> data = _processVideoFrame(*frame, width, height);
                    const auto timestampMicros = static_cast<int64_t>(
                            std::round(
                                    static_cast<double>(frame->best_effort_timestamp) * av_q2d(videoStream->time_base) *
                                    1000000)
                    );
                    return std::make_unique<Frame>(Frame::VIDEO, timestampMicros, data);
                }

                av_frame_unref(frame.get());
            }
        }

        av_packet_unref(packet.get());
    }

    return nullptr;
}

void Decoder::seekTo(long timestampMicros, bool keyframesOnly) {
    std::unique_lock<std::mutex> lock(mutex);

    if (timestampMicros < 0 || timestampMicros > format->durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    int ret = av_seek_frame(formatContext.get(), -1, timestampMicros, AVSEEK_FLAG_BACKWARD);
    if (ret < 0) {
        throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext.get());
    }

    if (!keyframesOnly) {
        auto packet = std::unique_ptr<AVPacket, av_packet_deleter>(av_packet_alloc());
        if (!packet) {
            throw DecoderException("Could not allocate AVPacket");
        }

        auto frame = std::unique_ptr<AVFrame, av_frame_deleter>(av_frame_alloc());
        if (!frame) {
            throw DecoderException("Could not allocate AVFrame");
        }

        bool found = false;

        int64_t maxFrames = 0;

        int64_t audioTimestamp = (audioStream) ? av_rescale_q(timestampMicros, AV_TIME_BASE_Q, audioStream->time_base)
                                               : 0;
        int64_t videoTimestamp = (videoStream) ? av_rescale_q(timestampMicros, AV_TIME_BASE_Q, videoStream->time_base)
                                               : 0;

        if (audioCodecContext && audioStream) {
            maxFrames = std::max(static_cast<int64_t>(static_cast<double>(timestampMicros) /
                                                      (static_cast<double>(format->durationMicros) /
                                                       static_cast<double>(audioStream->nb_frames))), maxFrames);
        }

        if (videoCodecContext && videoStream) {
            maxFrames = std::max(static_cast<int64_t>(static_cast<double>(timestampMicros) /
                                                      (static_cast<double>(format->durationMicros) /
                                                       static_cast<double>(videoStream->nb_frames))), maxFrames);
        }

        while (maxFrames-- > 0 && av_read_frame(formatContext.get(), packet.get()) >= 0) {
            if (audioCodecContext && audioStream && packet->stream_index == audioStream->index) {
                avcodec_send_packet(audioCodecContext.get(), packet.get());

                while (avcodec_receive_frame(audioCodecContext.get(), frame.get()) == 0) {
                    if (frame->best_effort_timestamp >= audioTimestamp) {
                        found = true;
                        break;
                    }
                }
            } else if (videoCodecContext && videoStream && packet->stream_index == videoStream->index) {
                avcodec_send_packet(videoCodecContext.get(), packet.get());

                while (avcodec_receive_frame(videoCodecContext.get(), frame.get()) == 0) {
                    if (frame->best_effort_timestamp >= videoTimestamp) {
                        found = true;
                        break;
                    }
                }
            }

            av_packet_unref(packet.get());

            if (found) break;
        }
    }
}

void Decoder::reset() {
    std::unique_lock<std::mutex> lock(mutex);

    if (av_seek_frame(formatContext.get(), -1, 0, AVSEEK_FLAG_FRAME) < 0) {
        throw DecoderException("Error resetting stream");
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext.get());
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext.get());
    }
}