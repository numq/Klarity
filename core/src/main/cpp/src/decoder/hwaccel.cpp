#include "hwaccel.h"

std::shared_mutex HardwareAcceleration::mutex;

std::map<AVHWDeviceType, AVBufferRef *> HardwareAcceleration::contexts;

std::vector<AVHWDeviceType> HardwareAcceleration::getAvailableHardwareAcceleration() {
    std::shared_lock<std::shared_mutex> lock(mutex);

    std::vector<AVHWDeviceType> available;

    AVHWDeviceType type = AV_HWDEVICE_TYPE_NONE;

    while ((type = av_hwdevice_iterate_types(type)) != AV_HWDEVICE_TYPE_NONE) {
        available.push_back(type);
    }

    return available;
}

AVBufferRef *HardwareAcceleration::requestContext(AVHWDeviceType type) {
    std::lock_guard<std::shared_mutex> lock(mutex);

    if (type == AV_HWDEVICE_TYPE_NONE) {
        return nullptr;
    }

    auto it = contexts.find(type);
    if (it != contexts.end()) {
        return av_buffer_ref(it->second);
    }

    AVBufferRef *ctx = nullptr;

    if (av_hwdevice_ctx_create(&ctx, type, nullptr, nullptr, 0) < 0) {
        return nullptr;
    }

    contexts[type] = ctx;

    return av_buffer_ref(ctx);
}

void HardwareAcceleration::releaseContext(AVBufferRef *ctx) {
    if (!ctx) return;

    av_buffer_unref(&ctx);
}

void HardwareAcceleration::cleanUp() {
    std::lock_guard<std::shared_mutex> lock(mutex);

    for (auto &[type, ctx]: contexts) {
        av_buffer_unref(&ctx);
    }

    contexts.clear();
}