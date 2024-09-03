#ifndef KLARITY_DECODER_DELETER_H
#define KLARITY_DECODER_DELETER_H

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

struct AvFormatContextDeleter {
    void operator()(AVFormatContext *ctx) const {
        if (ctx) {
            avformat_close_input(&ctx);
        }
    }
};

struct AvCodecContextDeleter {
    void operator()(AVCodecContext *ctx) const {
        if (ctx) {
            avcodec_free_context(&ctx);
        }
    }
};

struct AvPacketDeleter {
    void operator()(AVPacket *packet) const {
        if (packet) {
            av_packet_free(&packet);
        }
    }
};

struct AvFrameDeleter {
    void operator()(AVFrame *frame) const {
        if (frame) {
            av_frame_free(&frame);
        }
    }
};

struct SwsContextDeleter {
    void operator()(SwsContext *ctx) const {
        if (ctx) {
            sws_freeContext(ctx);
        }
    }
};

struct SwrContextDeleter {
    void operator()(SwrContext *ctx) const {
        if (ctx) {
            swr_free(&ctx);
        }
    }
};

#endif //KLARITY_DECODER_DELETER_H
