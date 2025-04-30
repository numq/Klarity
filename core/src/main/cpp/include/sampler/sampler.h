#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <memory>
#include <mutex>
#include <shared_mutex>
#include "exception.h"
#include "portaudio.h"
#include "stretch/stretch.h"

struct Sampler {
private:
    struct PaStreamDeleter {
        void operator()(PaStream *p) const {
            Pa_CloseStream(p);
        }
    };

    std::shared_mutex mutex;

    uint32_t sampleRate;

    uint32_t channels;

    std::unique_ptr<PaStream, PaStreamDeleter> stream;

    std::unique_ptr<signalsmith::stretch::SignalsmithStretch<float>> stretch;

    std::vector<float> samples;

    float playbackSpeedFactor = 1.0f;

    float volume = 1.0f;

public:
    explicit Sampler(uint32_t sampleRate, uint32_t channels);

    Sampler(const Sampler &) = delete;

    Sampler &operator=(const Sampler &) = delete;

    void setPlaybackSpeed(float factor);

    void setVolume(float value);

    int start();

    void play(const uint8_t *buffer, uint64_t size);

    void pause();

    void stop();
};

#endif //KLARITY_SAMPLER_H
