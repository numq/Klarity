#include "common.h"

std::shared_mutex decoderMutex;

std::shared_mutex samplerMutex;

std::unordered_map<jlong, std::unique_ptr<Decoder>> decoderPointers;

std::unordered_map<jlong, std::unique_ptr<Sampler>> samplerPointers;

jclass runtimeExceptionClass = nullptr;

jclass decoderExceptionClass = nullptr;

jclass hardwareAccelerationExceptionClass = nullptr;

jclass samplerExceptionClass = nullptr;

jclass formatClass = nullptr;

jmethodID formatConstructor = nullptr;

jclass audioFrameClass = nullptr;

jmethodID audioFrameConstructor = nullptr;

jclass videoFrameClass = nullptr;

jmethodID videoFrameConstructor = nullptr;

Decoder *getDecoderPointer(jlong handle) {
    auto it = decoderPointers.find(handle);

    if (it == decoderPointers.end()) {
        throw std::runtime_error("Invalid handle");
    }

    return it->second.get();
}

Sampler *getSamplerPointer(jlong handle) {
    auto it = samplerPointers.find(handle);

    if (it == samplerPointers.end()) {
        throw std::runtime_error("Invalid handle");
    }

    return it->second.get();
}

void deleteDecoderPointer(jlong handle) {
    if (decoderPointers.find(handle) == decoderPointers.end()) {
        throw std::runtime_error("Invalid handle");
    }

    erase_if(
            decoderPointers,
            [&handle](const auto &p) {
                return p.first == handle;
            }
    );
}

void deleteSamplerPointer(jlong handle) {
    if (samplerPointers.find(handle) == samplerPointers.end()) {
        throw std::runtime_error("Invalid handle");
    }

    erase_if(
            samplerPointers,
            [&handle](const auto &p) {
                return p.first == handle;
            }
    );
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_10) != JNI_OK) {
        return JNI_ERR;
    }

    runtimeExceptionClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("java/lang/RuntimeException"))
    );

    if (runtimeExceptionClass == nullptr) {
        return JNI_ERR;
    }

    decoderExceptionClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/decoder/DecoderException"))
    );

    if (decoderExceptionClass == nullptr) {
        return JNI_ERR;
    }

    hardwareAccelerationExceptionClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/hwaccel/HardwareAccelerationException"))
    );

    if (hardwareAccelerationExceptionClass == nullptr) {
        return JNI_ERR;
    }

    samplerExceptionClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/sampler/SamplerException"))
    );

    if (samplerExceptionClass == nullptr) {
        return JNI_ERR;
    }

    formatClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/format/NativeFormat"))
    );

    if (formatClass == nullptr) {
        return JNI_ERR;
    }

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(Ljava/lang/String;JIIIIDII)V");

    if (formatConstructor == nullptr) {
        return JNI_ERR;
    }

    audioFrameClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/frame/NativeAudioFrame"))
    );

    if (audioFrameClass == nullptr) {
        return JNI_ERR;
    }

    audioFrameConstructor = env->GetMethodID(audioFrameClass, "<init>", "(J[B)V");

    if (audioFrameConstructor == nullptr) {
        return JNI_ERR;
    }

    videoFrameClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/frame/NativeVideoFrame"))
    );

    if (videoFrameClass == nullptr) {
        return JNI_ERR;
    }

    videoFrameConstructor = env->GetMethodID(videoFrameClass, "<init>", "(J)V");

    if (videoFrameConstructor == nullptr) {
        return JNI_ERR;
    }

    av_log_set_level(AV_LOG_QUIET);

    if (Pa_Initialize() != paNoError) {
        return JNI_ERR;
    }

    return JNI_VERSION_10;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_10) != JNI_OK) return;

    {
        std::unique_lock<std::shared_mutex> decoderLock(decoderMutex);

        decoderPointers.clear();
    }

    {
        std::unique_lock<std::shared_mutex> samplerLock(samplerMutex);

        samplerPointers.clear();
    }

    Pa_Terminate();

    HardwareAcceleration::cleanUp();

    if (runtimeExceptionClass) {
        env->DeleteGlobalRef(runtimeExceptionClass);

        runtimeExceptionClass = nullptr;
    }

    if (decoderExceptionClass) {
        env->DeleteGlobalRef(decoderExceptionClass);

        decoderExceptionClass = nullptr;
    }

    if (hardwareAccelerationExceptionClass) {
        env->DeleteGlobalRef(hardwareAccelerationExceptionClass);

        hardwareAccelerationExceptionClass = nullptr;
    }

    if (samplerExceptionClass) {
        env->DeleteGlobalRef(samplerExceptionClass);

        samplerExceptionClass = nullptr;
    }

    if (formatClass) {
        env->DeleteGlobalRef(formatClass);

        formatClass = nullptr;
    }

    if (audioFrameClass) {
        env->DeleteGlobalRef(audioFrameClass);

        audioFrameClass = nullptr;
    }

    if (videoFrameClass) {
        env->DeleteGlobalRef(videoFrameClass);

        videoFrameClass = nullptr;
    }
}