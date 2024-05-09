#ifndef KLARITY_AUDIO_SAMPLER_H
#define KLARITY_AUDIO_SAMPLER_H

#include <iostream>
#include <memory>
#include <mutex>
#include <vector>
#include "openal/alc.h"
#include "openal/al.h"
#include "openal/alext.h"
#include "stretch/stretch.h"

class ISampler {
public:
    virtual ~ISampler() = default;

    virtual void setPlaybackSpeed(float factor) = 0;

    virtual bool setVolume(float value) = 0;

    virtual bool play(uint8_t *samples, uint64_t size) = 0;

    virtual void pause() = 0;

    virtual void resume() = 0;

    virtual void stop() = 0;
};

class Sampler : public ISampler {
private:
    const int32_t MIN_NUM_BUFFERS = 3;
    std::mutex mutex;
    ALenum format = AL_NONE;
    float playbackSpeedFactor = 1.0f;
    uint32_t source;
    uint32_t sampleRate;
    uint32_t channels;
    uint32_t numBuffers = MIN_NUM_BUFFERS;
    ALCdevice *device;
    ALCcontext *context;
    std::unique_ptr<signalsmith::stretch::SignalsmithStretch<float>> stretch;

    static void _checkALError(const char *file, int line);

    static ALenum _getFormat(uint32_t channels);

    void _discardQueuedBuffers() const;

    void _discardProcessedBuffers() const;

    void _initialize(uint32_t channels);

    void _cleanUp();

public:
    Sampler(uint32_t sampleRate, uint32_t channels);

    Sampler(uint32_t sampleRate, uint32_t channels, uint32_t numBuffers);

    ~Sampler() override;

    void setPlaybackSpeed(float factor) override;

    bool setVolume(float value) override;

    bool play(uint8_t *samples, uint64_t size) override;

    void pause() override;

    void resume() override;

    void stop() override;
};

#endif //KLARITY_AUDIO_SAMPLER_H
