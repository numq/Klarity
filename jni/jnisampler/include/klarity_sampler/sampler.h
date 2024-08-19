#ifndef KLARITY_SAMPLER_H
#define KLARITY_SAMPLER_H

#include <iostream>
#include <memory>
#include <mutex>
#include <vector>
#include "stretch/stretch.h"
#include "media.h"

class ISampler {
public:
    virtual ~ISampler() = default;

    virtual void setPlaybackSpeed(int64_t id, float factor) = 0;

    virtual void setVolume(int64_t id, float value) = 0;

    virtual void initialize(int64_t id, uint32_t sampleRate, uint32_t channels) = 0;

    virtual void start(int64_t id) = 0;

    virtual void play(int64_t id, uint8_t *samples, uint64_t size) = 0;

    virtual void stop(int64_t id) = 0;

    virtual void close(int64_t id) = 0;
};

class Sampler : public ISampler {
private:
    std::mutex mutex;
    std::unordered_map<int64_t, Media *> mediaPool{};

    Media *_acquireMedia(int64_t id);

    void _releaseMedia(int64_t id);

public:
    explicit Sampler();

    ~Sampler() override;

    void setPlaybackSpeed(int64_t id, float factor) override;

    void setVolume(int64_t id, float value) override;

    void initialize(int64_t id, uint32_t sampleRate, uint32_t channels) override;

    void start(int64_t id) override;

    void play(int64_t id, uint8_t *samples, uint64_t size) override;

    void stop(int64_t id) override;

    void close(int64_t id) override;
};

#endif //KLARITY_SAMPLER_H
