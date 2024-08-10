#ifndef KLARITY_SAMPLER_MEDIA_H
#define KLARITY_SAMPLER_MEDIA_H

#include <cstring>
#include <iostream>
#include <mutex>
#include "exception.h"
#include "stretch/stretch.h"
#include "portaudio.h"

struct Media {
private:
    std::mutex mutex;
    uint32_t sampleRate;
    uint32_t channels;
    PaSampleFormat format;
    PaStream *stream;
    signalsmith::stretch::SignalsmithStretch<float> *stretch;
    float playbackSpeedFactor = 1.0f;
    float volume = 1.0f;

public:
    explicit Media(uint32_t sampleRate, uint32_t channels);

    ~Media();

    void setPlaybackSpeed(float factor);

    void setVolume(float value);

    void start();

    void play(const uint8_t *samples, uint64_t size);

    void pause();

    void resume();

    void stop();
};

#endif //KLARITY_SAMPLER_MEDIA_H
