#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#define __STDC_CONSTANT_MACROS

#include <mutex>
#include <iostream>
#include <unordered_map>
#include "format.h"
#include "frame.h"
#include "media.h"

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

class IDecoder {
public:
    virtual ~IDecoder() = default;

    virtual void initialize(int64_t id, const char *location, bool findAudioStream, bool findVideoStream) = 0;

    virtual Format *getFormat(int64_t id) = 0;

    virtual Frame *nextFrame(int64_t id) = 0;

    virtual void seekTo(int64_t id, long timestampMicros) = 0;

    virtual void reset(int64_t id) = 0;

    virtual void close(int64_t id) = 0;
};

class Decoder : public IDecoder {
private:
    std::mutex mutex;
    std::unordered_map<int64_t, Media *> mediaPool{};

    Media *_acquireMedia(int64_t id);

    void _releaseMedia(int64_t id);

public:
    ~Decoder() override;

    void initialize(int64_t id, const char *location, bool findAudioStream, bool findVideoStream) override;

    Format *getFormat(int64_t id) override;

    Frame *nextFrame(int64_t id) override;

    void seekTo(int64_t id, long timestampMicros) override;

    void reset(int64_t id) override;

    void close(int64_t id) override;
};

#endif //KLARITY_DECODER_DECODER_H