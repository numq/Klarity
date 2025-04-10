#ifndef KLARITY_DECODER_FORMAT_H
#define KLARITY_DECODER_FORMAT_H

#include <string>

extern "C" {
#include "libavutil/hwcontext.h"
}

struct Format {
    std::string location;
    uint64_t durationMicros;
    uint32_t sampleRate = 0;
    uint32_t channels = 0;
    uint32_t width = 0;
    uint32_t height = 0;
    double frameRate = 0.0;
    AVHWDeviceType hwDeviceType = AVHWDeviceType::AV_HWDEVICE_TYPE_NONE;
    uint32_t audioBufferSize = 0;
    uint32_t videoBufferSize = 0;
};

#endif //KLARITY_DECODER_FORMAT_H
