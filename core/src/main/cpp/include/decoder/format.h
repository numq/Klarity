#ifndef KLARITY_DECODER_FORMAT_H
#define KLARITY_DECODER_FORMAT_H

#include <string>

extern "C" {
#include "libavutil/hwcontext.h"
}

struct Format {
    std::string location;
    int64_t durationMicros;
    int32_t sampleRate = 0;
    int32_t channels = 0;
    int32_t width = 0;
    int32_t height = 0;
    double frameRate = 0.0;
    AVHWDeviceType hwDeviceType = AV_HWDEVICE_TYPE_NONE;
};

#endif //KLARITY_DECODER_FORMAT_H
