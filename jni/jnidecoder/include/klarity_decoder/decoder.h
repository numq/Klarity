#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#define __STDC_CONSTANT_MACROS

#include <string>
#include <iostream>
#include <memory>
#include <mutex>
#include "frame.h"
#include "format.h"
#include "exception.h"
#include "deleter.h"

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

struct Decoder {
    std::mutex mutex;
    std::unique_ptr<Format> format;
    std::unique_ptr<AVFormatContext, AvFormatContextDeleter> formatContext;
    std::unique_ptr<AVCodecContext, AvCodecContextDeleter> audioCodecContext;
    std::unique_ptr<AVCodecContext, AvCodecContextDeleter> videoCodecContext;
    AVStream *audioStream;
    AVStream *videoStream;
    std::unique_ptr<SwsContext, SwsContextDeleter> swsContext;
    std::unique_ptr<SwrContext, SwrContextDeleter> swrContext;

private:
    std::vector<uint8_t> _processVideoFrame(const AVFrame &src, int64_t width, int64_t height);

    std::vector<uint8_t> _processAudioFrame(const AVFrame &src);

public:
    explicit Decoder(const std::string &location, bool findAudioStream, bool findVideoStream);

    std::unique_ptr<Frame> nextFrame(int64_t width, int64_t height);

    void seekTo(long timestampMicros, bool keyframesOnly);

    void reset();
};

#endif //KLARITY_DECODER_DECODER_H