#include "common.h"

jclass runtimeExceptionClass = nullptr;

jclass decoderExceptionClass = nullptr;

jclass hardwareAccelerationExceptionClass = nullptr;

jclass samplerExceptionClass = nullptr;

jclass formatClass = nullptr;

jmethodID formatConstructor = nullptr;

jclass frameClass = nullptr;

jmethodID frameConstructor = nullptr;

Decoder *getDecoderPointer(jlong handle) {
    auto decoder = reinterpret_cast<Decoder *>(handle);

    if (!decoder) {
        throw std::runtime_error("Invalid decoder handle");
    }

    return decoder;
}

Sampler *getSamplerPointer(jlong handle) {
    auto sampler = reinterpret_cast<Sampler *>(handle);

    if (!sampler) {
        throw std::runtime_error("Invalid sampler handle");
    }

    return sampler;
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

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(Ljava/lang/String;JIIIIDI)V");

    if (formatConstructor == nullptr) {
        return JNI_ERR;
    }

    frameClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/frame/NativeFrame"))
    );

    if (frameClass == nullptr) {
        return JNI_ERR;
    }

    frameConstructor = env->GetMethodID(frameClass, "<init>", "(JIJI)V");

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

    if (frameClass) {
        env->DeleteGlobalRef(frameClass);

        frameClass = nullptr;
    }
}