#ifndef KLARITY_DECODER_DELETER_H
#define KLARITY_DECODER_DELETER_H

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
}

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

#endif //KLARITY_DECODER_DELETER_H
