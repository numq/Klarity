#ifndef KLARITY_DECODER_DELETER_H
#define KLARITY_DECODER_DELETER_H

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

struct av_format_context_deleter {
    void operator()(AVFormatContext *context) const {
        if (context) {
            avformat_close_input(&context);
        }
    }
};

struct av_codec_context_deleter {
    void operator()(AVCodecContext *context) const {
        if (context) {
            avcodec_free_context(&context);
        }
    }
};

struct av_packet_deleter {
    void operator()(AVPacket *packet) const {
        if (packet) {
            av_packet_free(&packet);
        }
    }
};

struct av_frame_deleter {
    void operator()(AVFrame *frame) const {
        if (frame) {
            av_frame_free(&frame);
        }
    }
};

struct sws_context_deleter {
    void operator()(SwsContext *context) const {
        if (context) {
            sws_freeContext(context);
        }
    }
};

struct swr_context_deleter {
    void operator()(SwrContext *context) const {
        if (context) {
            swr_free(&context);
        }
    }
};

#endif //KLARITY_DECODER_DELETER_H
