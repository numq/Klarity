#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <mutex>
#include "exception.h"
#include "portaudio.h"
#include "stretch/stretch.h"

struct Sampler {
private:
    std::mutex mutex;

    uint32_t sampleRate;

    uint32_t channels;

    PaStream *stream = nullptr;

    std::unique_ptr<signalsmith::stretch::SignalsmithStretch<float>> stretch;

    float playbackSpeedFactor = 1.0f;

    float volume = 1.0f;

    void _cleanUp();

public:
    explicit Sampler(uint32_t sampleRate, uint32_t channels);

    ~Sampler();

    void setPlaybackSpeed(float factor);

    void setVolume(float value);

    int start();

    void play(const uint8_t *samples, uint64_t size);

    void pause();

    void stop();
};

#endif //KLARITY_SAMPLER_H
