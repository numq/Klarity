#ifndef KLARITY_DECODER_PARAMETERS_H
#define KLARITY_DECODER_PARAMETERS_H

#include <optional>
#include <vector>

extern "C" {
#include "libavutil/hwcontext.h"
}

struct AudioParameters {
    std::optional<uint32_t> sampleRate;
    std::optional<uint32_t> channels;
    std::optional<uint8_t *> buffer;
    std::optional<uint32_t> bufferSize;

    explicit AudioParameters(
            std::optional<uint32_t> sampleRate = std::nullopt,
            std::optional<uint32_t> channels = std::nullopt,
            std::optional<uint8_t *> buffer = std::nullopt,
            std::optional<uint32_t> bufferSize = std::nullopt
    ) : sampleRate(sampleRate), channels(channels), buffer(buffer), bufferSize(bufferSize) {}
};

struct VideoParameters {
    std::optional<uint32_t> width;
    std::optional<uint32_t> height;
    std::optional<double> frameRate;
    std::optional<uint32_t> hardwareAcceleration = AVHWDeviceType::AV_HWDEVICE_TYPE_NONE;
    std::optional<std::vector<uint32_t>> hardwareAccelerationFallbackCandidates;
    std::optional<bool> softwareAccelerationFallback;
    std::optional<uint8_t *> buffer;
    std::optional<uint32_t> bufferSize;

    explicit VideoParameters(
            std::optional<uint32_t> width = std::nullopt,
            std::optional<uint32_t> height = std::nullopt,
            std::optional<double> frameRate = std::nullopt,
            std::optional<uint32_t> hardwareAcceleration = std::nullopt,
            std::optional<std::vector<uint32_t>> hardwareAccelerationFallbackCandidates = std::nullopt,
            std::optional<bool> softwareAccelerationFallback = std::nullopt,
            std::optional<uint8_t *> buffer = std::nullopt,
            std::optional<uint32_t> bufferSize = std::nullopt
    ) : width(width),
        height(height),
        frameRate(frameRate),
        hardwareAcceleration(hardwareAcceleration),
        hardwareAccelerationFallbackCandidates(std::move(hardwareAccelerationFallbackCandidates)),
        softwareAccelerationFallback(softwareAccelerationFallback),
        buffer(buffer),
        bufferSize(bufferSize) {}
};

#endif //KLARITY_DECODER_PARAMETERS_H
