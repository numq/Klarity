#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <atomic>
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

    std::atomic<float> playbackSpeedFactor = 1.0f;

    std::atomic<float> volume = 1.0f;

public:
    explicit Sampler(uint32_t sampleRate, uint32_t channels);

    void setPlaybackSpeed(float factor);

    void setVolume(float value);

    int start();

    void play(const uint8_t *samples, uint64_t size);

    void pause();

    void stop();
};

#endif //KLARITY_SAMPLER_H
