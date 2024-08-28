#ifndef KLARITY_DECODER_MEDIA_H
#define KLARITY_DECODER_MEDIA_H

#include <string>
#include <mutex>
#include <iostream>
#include "frame.h"
#include "format.h"
#include "exception.h"

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

struct Media {
    std::mutex mutex;
    Format *format;
    AVFormatContext *formatContext;
    AVCodecContext *audioCodecContext = nullptr;
    AVCodecContext *videoCodecContext = nullptr;
    AVStream *audioStream = nullptr;
    AVStream *videoStream = nullptr;
    SwsContext *swsContext = nullptr;
    SwrContext *swrContext = nullptr;

private:
    std::vector<uint8_t> _processVideoFrame(const AVFrame &src, int64_t width, int64_t height);

    std::vector<uint8_t> _processAudioFrame(const AVFrame &src);

public:
    explicit Media(const char *location, bool findAudioStream, bool findVideoStream);

    ~Media();

    Frame *nextFrame(int64_t width, int64_t height);

    void seekTo(long timestampMicros, bool keyframesOnly);

    void reset();
};

#endif //KLARITY_DECODER_MEDIA_H
