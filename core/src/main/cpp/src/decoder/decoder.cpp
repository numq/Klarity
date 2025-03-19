#include "decoder.h"

AVBufferRef *Decoder::_initializeHWDevice(HardwareAcceleration hwAccel) {
    AVBufferRef *hw_device_ctx = nullptr;

    AVHWDeviceType type = AV_HWDEVICE_TYPE_NONE;

    switch (hwAccel) {
        case HardwareAcceleration::CUDA:
            type = AV_HWDEVICE_TYPE_CUDA;
            break;

        case HardwareAcceleration::VAAPI:
            type = AV_HWDEVICE_TYPE_VAAPI;
            break;

        case HardwareAcceleration::DXVA2:
            type = AV_HWDEVICE_TYPE_DXVA2;
            break;

        case HardwareAcceleration::QSV:
            type = AV_HWDEVICE_TYPE_QSV;
            break;

        default:
            return nullptr;
    }

    if (av_hwdevice_ctx_create(&hw_device_ctx, type, nullptr, nullptr, 0) < 0) {
        throw DecoderException("Failed to create hardware device context");
    }

    return hw_device_ctx;
}


AVCodecContext *Decoder::_initializeCodecContext(AVCodecParameters *avCodecParameters, HardwareAcceleration hwAccel) {
    const AVCodec *codec = nullptr;

    switch (hwAccel) {
        case HardwareAcceleration::CUDA:
            codec = avcodec_find_decoder_by_name("h264_cuvid");
            break;

        case HardwareAcceleration::VAAPI:
            codec = avcodec_find_decoder_by_name("h264_vaapi");
            break;

        case HardwareAcceleration::DXVA2:
            codec = avcodec_find_decoder_by_name("h264_dxva2");
            break;

        case HardwareAcceleration::QSV:
            codec = avcodec_find_decoder_by_name("h264_qsv");
            break;

        default:
            codec = avcodec_find_decoder(avCodecParameters->codec_id);
            break;
    }

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

    if (hwAccel != HardwareAcceleration::NONE) {
        codecContext->hw_device_ctx = _initializeHWDevice(hwAccel);
        if (!codecContext->hw_device_ctx) {
            avcodec_free_context(&codecContext);
            throw DecoderException("Failed to initialize hardware device context");
        }
    }

    if (avcodec_open2(codecContext, codec, nullptr) < 0) {
        if (codecContext->hw_device_ctx) {
            av_buffer_unref(&codecContext->hw_device_ctx);
        }
        avcodec_free_context(&codecContext);
        throw DecoderException("Could not open codec");
    }

    return codecContext;
}

void Decoder::_prepareSwsContext(AVPixelFormat srcFormat, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
    if (swsPixelFormat != pixelFormat || swsWidth != dstWidth || swsHeight != dstHeight) {
        swsContext = sws_getCachedContext(
                swsContext,
                srcWidth, srcHeight, srcFormat,
                dstWidth, dstHeight, pixelFormat,
                swsFlags,
                nullptr, nullptr, nullptr
        );

        if (!swsContext) {
            swsPixelFormat = AV_PIX_FMT_NONE;

            swsWidth = -1;

            swsHeight = -1;

            throw DecoderException("Could not initialize sws context");
        }

        swsPixelFormat = pixelFormat;

        swsWidth = dstWidth;

        swsHeight = dstHeight;
    }
}

void Decoder::_processAudioFrame(const AVFrame &src) {
    if (format.sampleRate <= 0 || format.channels <= 0) {
        throw DecoderException("Invalid audio format");
    }

    auto nbSamples = av_rescale_rnd(
            swr_get_delay(swrContext, src.sample_rate) + src.nb_samples,
            format.sampleRate,
            src.sample_rate,
            AV_ROUND_UP
    );

    auto bufferSize = av_samples_get_buffer_size(
            nullptr,
            src.ch_layout.nb_channels,
            static_cast<int>(nbSamples),
            sampleFormat,
            1
    );
    if (bufferSize < 0) {
        throw DecoderException("Could not get buffer size, error: " + std::to_string(bufferSize));
    }

    if (audioBuffer.size() != bufferSize) {
        audioBuffer.resize(bufferSize);
    }

    if (src.format != sampleFormat) {
        uint8_t *outPlanes[AV_NUM_DATA_POINTERS] = {nullptr};

        int filledSize = av_samples_fill_arrays(
                outPlanes,
                nullptr,
                audioBuffer.data(),
                src.ch_layout.nb_channels,
                static_cast<int>(nbSamples),
                sampleFormat,
                1
        );

        if (filledSize < 0) {
            throw DecoderException("Error while filling audio buffer, error: " + std::to_string(filledSize));
        }

        swr_convert(
                swrContext,
                outPlanes,
                static_cast<int>(nbSamples),
                const_cast<const uint8_t **>(src.data),
                src.nb_samples
        );
    } else {
        if (av_sample_fmt_is_planar(sampleFormat)) {
            for (int ch = 0; ch < src.ch_layout.nb_channels; ch++) {
                int channel_size = av_samples_get_buffer_size(
                        nullptr, 1, static_cast<int>(nbSamples), sampleFormat, 1
                );
                if (channel_size < 0) {
                    throw DecoderException("Could not get per-channel buffer size");
                }
                memcpy(audioBuffer.data() + ch * channel_size, src.data[ch], channel_size);
            }
        } else {
            memcpy(audioBuffer.data(), src.data[0], bufferSize);
        }
    }
}

void Decoder::_processVideoFrame(const AVFrame &src, int dstWidth, int dstHeight) {
    if (static_cast<AVPixelFormat>(src.format) == AV_PIX_FMT_NONE) {
        throw DecoderException("Invalid pixel format");
    }

    if (format.width <= 0 || format.height <= 0) {
        throw DecoderException("Invalid video format");
    }

    if (dstWidth <= 0 || dstHeight <= 0) {
        throw DecoderException("Invalid destination dimensions");
    }

    auto srcFormat = static_cast<AVPixelFormat>(src.format);

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

    auto srcWidth = src.width;

    auto srcHeight = src.height;

    _prepareSwsContext(srcFormat, srcWidth, srcHeight, dstWidth, dstHeight);

    auto bufferSize = av_image_get_buffer_size(static_cast<AVPixelFormat>(swsPixelFormat), swsWidth, swsHeight, 1);
    if (bufferSize < 0) {
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
        throw DecoderException("Could not fill linesizes");
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
            swsContext,
            src.data,
            src.linesize,
            0,
            srcHeight,
            dst.data(),
            dstLineSize.data()
    ) < 0) {
        throw DecoderException("Error while converting the video frame");
    }
}

void Decoder::_cleanUp() {
    if (frame) {
        av_frame_free(&frame);
    }

    if (packet) {
        av_packet_free(&packet);
    }

    if (swrContext) {
        swr_free(&swrContext);
        swrContext = nullptr;
    }

    if (swsContext) {
        sws_freeContext(swsContext);
        swsContext = nullptr;
    }

    if (audioCodecContext) {
        avcodec_free_context(&audioCodecContext);
        audioCodecContext = nullptr;
    }

    if (videoCodecContext) {
        avcodec_free_context(&videoCodecContext);
        videoCodecContext = nullptr;
    }

    if (formatContext) {
        avformat_close_input(&formatContext);
        formatContext = nullptr;
    }

    if (hwDeviceContext) {
        av_buffer_unref(&hwDeviceContext);
    }

    std::vector<uint8_t>().swap(audioBuffer);

    std::vector<uint8_t>().swap(videoBuffer);
}

Decoder::Decoder(
        const std::string &location,
        bool findAudioStream,
        bool findVideoStream,
        HardwareAcceleration hwAccel
) {
    formatContext = avformat_alloc_context();
    if (!formatContext) {
        throw DecoderException("Could not allocate format context");
    }

    int ret = avformat_open_input(&formatContext, location.c_str(), nullptr, nullptr);
    if (ret < 0) {
        avformat_free_context(formatContext);
        throw DecoderException("Could not open input stream for location: " + location);
    }

    try {
        if (avformat_find_stream_info(formatContext, nullptr) < 0) {
            avformat_close_input(&formatContext);
            throw DecoderException("Could not find stream information");
        }

        format = Format{
                location,
                formatContext->duration == AV_NOPTS_VALUE ? 0 : static_cast<uint64_t>(formatContext->duration)
        };

        for (int streamIndex = 0; streamIndex < formatContext->nb_streams; streamIndex++) {
            auto avCodecParameters = formatContext->streams[streamIndex]->codecpar;

            if (avCodecParameters) {
                if (findAudioStream && avCodecParameters->codec_type == AVMEDIA_TYPE_AUDIO) {
                    audioCodecContext = _initializeCodecContext(avCodecParameters, HardwareAcceleration::NONE);

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
                    videoCodecContext = _initializeCodecContext(avCodecParameters, hwAccel);

                    videoStreamIndex = streamIndex;

                    swsContext = sws_getContext(
                            videoCodecContext->width, videoCodecContext->height, videoCodecContext->pix_fmt,
                            videoCodecContext->width, videoCodecContext->height, pixelFormat,
                            swsFlags,
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

                    format.frameRate = av_q2d(formatContext->streams[videoStreamIndex]->avg_frame_rate);
                }
            }
        }

        isSeekable = audioStreamIndex != -1 || (videoStreamIndex != -1 &&
                                                static_cast<double>(formatContext->streams[videoStreamIndex]->nb_frames) >=
                                                format.frameRate);

        if (!isSeekable) {
            format.durationMicros = 0;
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

    if (!packet) {
        packet = av_packet_alloc();

        if (!packet) throw DecoderException("Memory allocation failed for packet");
    }

    av_packet_unref(packet);

    if (!frame) {
        frame = av_frame_alloc();

        if (!frame) throw DecoderException("Memory allocation failed for frame");
    }

    av_frame_unref(frame);

    while (av_read_frame(formatContext, packet) == 0) {
        if (audioCodecContext && audioStreamIndex != -1 && packet->stream_index == audioStreamIndex) {
            if (avcodec_send_packet(audioCodecContext, packet) < 0) {
                av_packet_unref(packet);

                continue;
            }

            int ret;
            while ((ret = avcodec_receive_frame(audioCodecContext, frame)) == 0) {
                _processAudioFrame(*frame);

                const auto timestampMicros = frame->best_effort_timestamp == AV_NOPTS_VALUE ? 0 : av_rescale_q(
                        frame->best_effort_timestamp,
                        formatContext->streams[audioStreamIndex]->time_base,
                        AVRational{1, 1'000'000}
                );

                av_packet_unref(packet);

                return Frame{Frame::AUDIO, timestampMicros, audioBuffer};
            }

            if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                throw DecoderException("Error while receiving audio frame");
            }
        } else if (videoCodecContext && videoStreamIndex != -1 && packet->stream_index == videoStreamIndex) {
            if (avcodec_send_packet(videoCodecContext, packet) < 0) {
                av_packet_unref(packet);

                continue;
            }

            int ret;
            while ((ret = avcodec_receive_frame(videoCodecContext, frame)) == 0) {
                _processVideoFrame(*frame, width, height);

                const auto timestampMicros = frame->best_effort_timestamp == AV_NOPTS_VALUE ? 0 : av_rescale_q(
                        frame->best_effort_timestamp,
                        formatContext->streams[videoStreamIndex]->time_base,
                        AVRational{1, 1'000'000}
                );

                av_packet_unref(packet);

                return Frame{Frame::VIDEO, timestampMicros, videoBuffer};
            }

            if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                throw DecoderException("Error while receiving video frame");
            }
        }

        av_packet_unref(packet);
    }

    return std::nullopt;
}

void Decoder::seekTo(long timestampMicros, bool keyframesOnly) {
    std::unique_lock<std::mutex> lock(mutex);

    if (!isSeekable || (audioStreamIndex == -1 && videoStreamIndex == -1)) {
        return;
    }

    if (timestampMicros < 0 || timestampMicros > format.durationMicros) {
        throw DecoderException("Timestamp out of bounds");
    }

    auto streamIndex = videoStreamIndex != -1 ? videoStreamIndex : audioStreamIndex;

    auto timestamp = av_rescale_q(timestampMicros, {1, AV_TIME_BASE},
                                  formatContext->streams[streamIndex]->time_base);

    int seekFlags = AVSEEK_FLAG_BACKWARD;

    if (!keyframesOnly) {
        seekFlags |= AVSEEK_FLAG_ANY;
    }

    if (av_seek_frame(formatContext, streamIndex, timestamp, seekFlags) < 0) {
        throw DecoderException("Error seeking to timestamp: " + std::to_string(timestampMicros));
    }

    if (audioCodecContext) {
        avcodec_flush_buffers(audioCodecContext);
    }

    if (videoCodecContext) {
        avcodec_flush_buffers(videoCodecContext);

        if (streamIndex == videoStreamIndex) {
            bool found = false;

            if (!packet) {
                packet = av_packet_alloc();

                if (!packet) throw DecoderException("Memory allocation failed for packet");
            }

            av_packet_unref(packet);

            if (!frame) {
                frame = av_frame_alloc();

                if (!frame) throw DecoderException("Memory allocation failed for frame");
            }

            av_frame_unref(frame);

            while (av_read_frame(formatContext, packet) == 0) {
                if (packet->stream_index == streamIndex && (!(keyframesOnly && !(packet->flags & AV_PKT_FLAG_KEY)))) {
                    if (avcodec_send_packet(videoCodecContext, packet) < 0) {
                        av_packet_unref(packet);

                        continue;
                    }

                    if ((avcodec_receive_frame(videoCodecContext, frame)) >= 0) {
                        av_frame_unref(frame);

                        found = true;
                    }
                }

                av_packet_unref(packet);

                if (found) {
                    break;
                }
            }
        }
    }
}

void Decoder::reset() {
    std::unique_lock<std::mutex> lock(mutex);

    if (!isSeekable || (audioStreamIndex == -1 && videoStreamIndex == -1)) {
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