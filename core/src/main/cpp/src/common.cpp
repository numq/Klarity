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

jclass frameClass = nullptr;

jmethodID frameConstructor = nullptr;

void handleRuntimeException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(runtimeExceptionClass, errorMessage.c_str());
}

void handleDecoderException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(decoderExceptionClass, errorMessage.c_str());
}

void handleHardwareAccelerationException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(hardwareAccelerationExceptionClass, errorMessage.c_str());
}

void handleSamplerException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(samplerExceptionClass, errorMessage.c_str());
}

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
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/decoder/HardwareAccelerationException"))
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

    formatClass = (jclass) env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/format/NativeFormat"));
    if (formatClass == nullptr) {
        return JNI_ERR;
    }

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(Ljava/lang/String;JIIIID)V");
    if (formatConstructor == nullptr) {
        return JNI_ERR;
    }

    frameClass = (jclass) env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/frame/NativeFrame"));
    if (frameClass == nullptr) {
        return JNI_ERR;
    }

    frameConstructor = env->GetMethodID(frameClass, "<init>", "(IJ[B)V");
    if (frameConstructor == nullptr) {
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

    if (frameClass) {
        env->DeleteGlobalRef(frameClass);
        frameClass = nullptr;
    }
}