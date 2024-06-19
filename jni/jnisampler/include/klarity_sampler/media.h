#ifndef KLARITY_SAMPLER_MEDIA_H
#define KLARITY_SAMPLER_MEDIA_H

#include <iostream>
#include <mutex>
#include "stretch/stretch.h"
#include "al.h"
#include "alc.h"
#include "alext.h"

struct Media {
    uint32_t sampleRate;
    uint32_t channels;
    uint32_t numBuffers;
    ALenum format = AL_NONE;
    ALuint source = AL_NONE;
    signalsmith::stretch::SignalsmithStretch<float> *stretch;
    float playbackSpeedFactor = 1.0f;

private:
    std::mutex mutex;

    static void _checkALError(const char *file, int line);

    void _discardQueuedBuffers() const;

    void _discardProcessedBuffers() const;

public:
    explicit Media(uint32_t sampleRate, uint32_t channels, uint32_t numBuffers);

    ~Media();

    float getCurrentTime();

    void setPlaybackSpeed(float factor);

    bool setVolume(float value);

    bool play(uint8_t *samples, uint64_t size);

    void pause();

    void resume();

    void stop();
};

#endif //KLARITY_SAMPLER_MEDIA_H
