#ifndef KLARITY_DECODER_HWACCEL_H
#define KLARITY_DECODER_HWACCEL_H

#include <map>
#include <memory>
#include <iostream>
#include <vector>
#include <shared_mutex>
#include "deleter.h"
#include "exception.h"

extern "C" {
#include <libavcodec/avcodec.h>
}

class HardwareAcceleration {
private:
    static std::shared_mutex mutex;

    static std::map<AVHWDeviceType, std::shared_ptr<AVBufferRef>> contexts;

public:
    static std::vector<AVHWDeviceType> getAvailableHardwareAcceleration();

    static AVBufferRef *requestContext(AVHWDeviceType type);

    static void releaseContext(AVBufferRef *ctx);

    static void cleanUp();
};

#endif //KLARITY_DECODER_HWACCEL_H