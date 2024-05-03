#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#define __STDC_CONSTANT_MACROS

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

#include "format.h"
#include "frame.h"
#include <mutex>
#include <iostream>

class IDecoder {
public:
    Format *format = nullptr;

    virtual ~IDecoder() = default;

    virtual Frame *readFrame(bool doVideo, bool doAudio) = 0;

    virtual void seekTo(long timestampMicros) = 0;

    virtual void reset() = 0;
};

class Decoder : public IDecoder {
private:
    std::mutex mutex;

protected:
    AVFormatContext *formatContext = nullptr;
    AVCodecContext *videoCodecContext = nullptr;
    AVCodecContext *audioCodecContext = nullptr;
    AVStream *videoStream = nullptr;
    AVStream *audioStream = nullptr;
    SwsContext *swsContext = nullptr;
    SwrContext *swrContext = nullptr;

    Frame *_readFrame(bool doVideo, bool doAudio);

    std::vector<uint8_t> _processVideoFrame(const AVFrame &src);

    std::vector<uint8_t> _processAudioFrame(const AVFrame &src);

    void _seekTo(long timestampMicros);

    void _reset();

    void _cleanUp();

public:
    Decoder(const std::string &location, bool findAudioStream, bool findVideoStream);

    ~Decoder() override;

    Frame *readFrame(bool doVideo, bool doAudio) override;

    void seekTo(long timestampMicros) override;

    void reset() override;
};

#endif //KLARITY_DECODER_DECODER_H
