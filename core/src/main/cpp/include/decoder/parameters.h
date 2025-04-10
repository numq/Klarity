#ifndef KLARITY_DECODER_PARAMETERS_H
#define KLARITY_DECODER_PARAMETERS_H

#include <optional>
#include <utility>
#include <vector>

extern "C" {
#include "libavutil/hwcontext.h"
}

struct AudioParameters {
    struct DecodingParameters {
        std::optional<uint32_t> sampleRate;
        std::optional<uint32_t> channels;
    };

    std::optional<DecodingParameters> decodingParameters;
};

struct VideoParameters {
    struct DecodingParameters {
        std::optional<uint32_t> width;
        std::optional<uint32_t> height;
        std::optional<double> frameRate;
        std::vector<uint32_t> hardwareAccelerationCandidates;
    };

    std::optional<DecodingParameters> decodingParameters;
};

#endif //KLARITY_DECODER_PARAMETERS_H
