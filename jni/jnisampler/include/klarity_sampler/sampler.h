#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <iostream>
#include <memory>
#include <mutex>
#include <vector>
#include "openal/alc.h"
#include "openal/al.h"
#include "openal/alext.h"
#include "stretch/stretch.h"
#include "media.h"

class ISampler {
public:
    virtual ~ISampler() = default;

    virtual float getCurrentTime(uint64_t id) = 0;

    virtual void setPlaybackSpeed(uint64_t id, float factor) = 0;

    virtual bool setVolume(uint64_t id, float value) = 0;

    virtual bool initialize(uint64_t id, uint32_t sampleRate, uint32_t channels, uint32_t numBuffers) = 0;

    virtual bool play(uint64_t id, uint8_t *samples, uint64_t size) = 0;

    virtual void pause(uint64_t id) = 0;

    virtual void resume(uint64_t id) = 0;

    virtual void stop(uint64_t id) = 0;

    virtual void close(uint64_t id) = 0;
};

class Sampler : public ISampler {
private:
    std::mutex mutex;
    ALCdevice *device;
    ALCcontext *context;
    std::unordered_map<uint64_t, Media *> mediaPool{};

    static void _checkALError(const char *file, int line);

    Media *_acquireMedia(uint64_t id);

    void _releaseMedia(uint64_t id);

public:
    Sampler();

    ~Sampler() override;

    float getCurrentTime(uint64_t id) override;

    void setPlaybackSpeed(uint64_t id, float factor) override;

    bool setVolume(uint64_t id, float value) override;

    bool initialize(uint64_t id, uint32_t sampleRate, uint32_t channels, uint32_t numBuffers) override;

    bool play(uint64_t id, uint8_t *samples, uint64_t size) override;

    void pause(uint64_t id) override;

    void resume(uint64_t id) override;

    void stop(uint64_t id) override;

    void close(uint64_t id) override;
};

#endif //KLARITY_SAMPLER_H
