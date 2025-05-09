#include "common.h"

jclass runtimeExceptionClass = nullptr;

jclass decoderExceptionClass = nullptr;

jclass hardwareAccelerationExceptionClass = nullptr;

jclass poolExceptionClass = nullptr;

jclass samplerExceptionClass = nullptr;

jclass formatClass = nullptr;

jmethodID formatConstructor = nullptr;

jclass audioFrameClass = nullptr;

jmethodID audioFrameConstructor = nullptr;

jclass videoFrameClass = nullptr;

jmethodID videoFrameConstructor = nullptr;

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

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) {
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

    poolExceptionClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/pool/PoolException"))
    );

    if (poolExceptionClass == nullptr) {
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

    audioFrameConstructor = env->GetMethodID(audioFrameClass, "<init>", "([BJ)V");

    if (audioFrameConstructor == nullptr) {
        return JNI_ERR;
    }

    videoFrameClass = reinterpret_cast<jclass>(
            env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/frame/NativeVideoFrame"))
    );

    if (videoFrameClass == nullptr) {
        return JNI_ERR;
    }

    videoFrameConstructor = env->GetMethodID(videoFrameClass, "<init>", "(IJ)V");

    if (videoFrameConstructor == nullptr) {
        return JNI_ERR;
    }

    av_log_set_level(AV_LOG_QUIET);

    if (Pa_Initialize() != paNoError) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) return;

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

    if (poolExceptionClass) {
        env->DeleteGlobalRef(poolExceptionClass);

        poolExceptionClass = nullptr;
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