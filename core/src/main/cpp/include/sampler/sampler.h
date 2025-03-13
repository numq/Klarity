#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <cstring>
#include <iostream>
#include <memory>
#include <mutex>
#include "exception.h"
#include "portaudio.h"
#include "stretch/stretch.h"
#include "sampler-deleter.h"

struct Sampler {
private:
    std::mutex mutex;

    uint32_t sampleRate;

    uint32_t channels;

    std::unique_ptr<PaStream, pa_stream_deleter> stream;

    std::unique_ptr<signalsmith::stretch::SignalsmithStretch<float>, signalsmith_stretch_deleter> stretch;

    float playbackSpeedFactor = 1.0f;

    float volume = 1.0f;

public:
    explicit Sampler(uint32_t sampleRate, uint32_t channels);

    void setPlaybackSpeed(float factor);

    void setVolume(float value);

    int start();

    void play(const uint8_t *samples, uint64_t size);

    void stop();
};

#endif //KLARITY_SAMPLER_H
