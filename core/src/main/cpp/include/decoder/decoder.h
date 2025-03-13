#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#include <string>
#include <iostream>
#include <memory>
#include <mutex>
#include "frame.h"
#include "format.h"
#include "exception.h"
#include "decoder-deleter.h"

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

struct Decoder {
private:
    std::mutex mutex;

    std::unique_ptr<AVFormatContext, av_format_context_deleter> formatContext;

    std::unique_ptr<AVCodecContext, av_codec_context_deleter> audioCodecContext;

    std::unique_ptr<AVCodecContext, av_codec_context_deleter> videoCodecContext;

    std::unique_ptr<AVStream> audioStream;

    std::unique_ptr<AVStream> videoStream;

    std::unique_ptr<SwsContext, sws_context_deleter> swsContext;

    std::unique_ptr<SwrContext, swr_context_deleter> swrContext;

    std::vector<uint8_t> _processAudioFrame(const AVFrame &src);

    std::vector<uint8_t> _processVideoFrame(const AVFrame &src, int64_t width, int64_t height);

public:
    std::unique_ptr<Format> format{nullptr};

    explicit Decoder(const std::string &location, bool findAudioStream, bool findVideoStream);

    std::unique_ptr<Frame> nextFrame(int64_t width, int64_t height);

    void seekTo(long timestampMicros, bool keyframesOnly);

    void reset();
};

#endif //KLARITY_DECODER_DECODER_H