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
#include "media.h"
#include <mutex>
#include <iostream>
#include <unordered_map>

class IDecoder {
public:
    virtual ~IDecoder() = default;

    virtual bool initialize(uint64_t id, const char *location, bool findAudioStream, bool findVideoStream) = 0;

    virtual Format *getFormat(uint64_t id) = 0;

    virtual Frame *nextFrame(uint64_t id) = 0;

    virtual void seekTo(uint64_t id, long timestampMicros) = 0;

    virtual void reset(uint64_t id) = 0;

    virtual void close(uint64_t id) = 0;
};

class Decoder : public IDecoder {
private:
    std::mutex mutex;
    std::unordered_map<uint64_t, Media *> mediaPool{};

    Media *_acquireMedia(uint64_t id);

    void _releaseMedia(uint64_t id);

public:
    ~Decoder() override;

    bool initialize(uint64_t id, const char *location, bool findAudioStream, bool findVideoStream) override;

    Format *getFormat(uint64_t id) override;

    Frame *nextFrame(uint64_t id) override;

    void seekTo(uint64_t id, long timestampMicros) override;

    void reset(uint64_t id) override;

    void close(uint64_t id) override;
};

#endif //KLARITY_DECODER_DECODER_H