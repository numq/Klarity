#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <memory>
#include <mutex>
#include <shared_mutex>
#include "exception.h"
#include "stretch/stretch.h"
#include <portaudio.h>

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

public:
    explicit Sampler(uint32_t sampleRate, uint32_t channels);

    Sampler(const Sampler &) = delete;

    Sampler &operator=(const Sampler &) = delete;

    int start();

    void write(const uint8_t *buffer, int size, float volume, float playbackSpeedFactor);

    void stop();

    void flush();

    void drain(float volume, float playbackSpeedFactor);
};

#endif //KLARITY_SAMPLER_H
