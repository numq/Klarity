#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#include <map>
#include <shared_mutex>
#include <optional>
#include <string>
#include <functional>
#include "exception.h"
#include "format.h"
#include "frame.h"
#include "hwaccel.h"

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/imgutils.h"
#include <libavutil/opt.h>
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
}

class Decoder {
private:
    struct AVFormatContextDeleter {
        void operator()(AVFormatContext *p) const {
            avformat_close_input(&p);
        }
    };

    struct AVCodecContextDeleter {
        void operator()(AVCodecContext *p) const {
            p->get_format = nullptr;

            p->opaque = nullptr;

            if (p->hw_device_ctx) {
                av_buffer_unref(&p->hw_device_ctx);

                p->hw_device_ctx = nullptr;
            }

            avcodec_free_context(&p);
        }
    };

    struct SwrContextDeleter {
        void operator()(SwrContext *p) const {
            swr_free(&p);
        }
    };

    struct SwsContextDeleter {
        void operator()(SwsContext *p) const {
            sws_freeContext(p);
        }
    };

    struct AVPacketDeleter {
        void operator()(AVPacket *p) const {
            av_packet_free(&p);
        }
    };

    struct AVFrameDeleter {
        void operator()(AVFrame *p) const {
            av_frame_free(&p);
        }
    };

    struct AVBufferRefDeleter {
        void operator()(AVBufferRef *p) const {
            av_buffer_unref(&p);
        }
    };

    std::shared_mutex mutex;

    const AVSampleFormat sampleFormat = AV_SAMPLE_FMT_FLT;

    const AVPixelFormat pixelFormat = AV_PIX_FMT_RGBA;

    const int swsFlags = SWS_BILINEAR;

    std::vector<uint8_t> audioBuffer;

    std::vector<uint8_t> videoBuffer;

    std::unique_ptr<AVFormatContext, AVFormatContextDeleter> formatContext;

    std::unique_ptr<AVCodecContext, AVCodecContextDeleter> audioCodecContext;

    std::unique_ptr<AVCodecContext, AVCodecContextDeleter> videoCodecContext;

    const AVStream *audioStream = nullptr;

    const AVStream *videoStream = nullptr;

    const AVCodec *audioDecoder = nullptr;

    const AVCodec *videoDecoder = nullptr;

    bool isSeekable = true;

    std::unique_ptr<SwrContext, SwrContextDeleter> swrContext;

    std::unique_ptr<SwsContext, SwsContextDeleter> swsContext;

    int swsPixelFormat = AV_PIX_FMT_NONE;

    int swsWidth = -1;

    int swsHeight = -1;

    std::unique_ptr<AVPacket, AVPacketDeleter> packet;

    std::unique_ptr<AVFrame, AVFrameDeleter> audioFrame;

    std::unique_ptr<AVFrame, AVFrameDeleter> swVideoFrame;

    std::unique_ptr<AVFrame, AVFrameDeleter> hwVideoFrame;

    std::unique_ptr<AVBufferRef, AVBufferRefDeleter> hwDeviceContext;

    AVHWDeviceType hwDeviceType = AVHWDeviceType::AV_HWDEVICE_TYPE_NONE;

    void _prepareHardwareAcceleration();

    void _prepareSwsContext(AVPixelFormat srcFormat, int width, int height, int dstWidth, int dstHeight);

    void _processAudioFrame();

    void _processVideoFrame(int dstWidth, int dstHeight);

    void _transferFrameData();

public:
    Format format;

    Decoder(
            const std::string &location,
            bool findAudioStream,
            bool findVideoStream,
            int hwDeviceType
    );

    ~Decoder();

    std::optional<Frame> nextFrame(int width, int height);

    void seekTo(long timestampMicros, bool keyframesOnly);

    void reset();
};

#endif // KLARITY_DECODER_DECODER_H