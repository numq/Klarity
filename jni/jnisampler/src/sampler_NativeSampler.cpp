#include <iostream>
#include "sampler_NativeSampler.h"
#include "klarity_sampler/sampler.h"

static jclass exceptionClass;

void handleException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(exceptionClass, ("JNI ERROR: " + errorMessage).c_str());
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) {
        throw std::runtime_error("Failed to get JNI environment");
    }

    exceptionClass = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/RuntimeException")));
    if (exceptionClass == nullptr) {
        throw std::runtime_error("Failed to find java/lang/RuntimeException class");
    }

    PaError err = Pa_Initialize();
    if (err != paNoError) {
        throw SamplerException("Failed to initialize PortAudio: " + std::string(Pa_GetErrorText(err)));
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) env->DeleteGlobalRef(exceptionClass);

    PaError err = Pa_Terminate();
    if (err != paNoError) {
        throw SamplerException("Failed to terminate PortAudio: " + std::string(Pa_GetErrorText(err)));
    }
}

JNIEXPORT jobject JNICALL Java_sampler_NativeSampler_initializeNative(
        JNIEnv *env,
        jobject thisObject,
        jint sampleRate,
        jint channels
) {
    try {
        auto sampler = new Sampler(static_cast<uint32_t>(sampleRate), static_cast<uint32_t>(channels));
        return env->NewDirectByteBuffer(sampler, 0);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in initNative method: ") + e.what());
        return nullptr;
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jobject thisObject,
        jobject nativeHandle,
        jfloat factor
) {
    try {
        auto sampler = reinterpret_cast<Sampler *>(env->GetDirectBufferAddress(nativeHandle));
        if (!sampler) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        sampler->setPlaybackSpeed(static_cast<float>(factor));
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setPlaybackSpeedNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jobject thisObject,
        jobject nativeHandle,
        jfloat value
) {
    try {
        auto sampler = reinterpret_cast<Sampler *>(env->GetDirectBufferAddress(nativeHandle));
        if (!sampler) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        sampler->setVolume(static_cast<float>(value));
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setVolumeNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_startNative(JNIEnv *env, jobject thisObject, jobject nativeHandle) {
    try {
        auto sampler = reinterpret_cast<Sampler *>(env->GetDirectBufferAddress(nativeHandle));
        if (!sampler) {
            throw std::runtime_error("Invalid nativeHandle");
        }
        sampler->start();
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in startNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_playNative(
        JNIEnv *env,
        jobject thisObject,
        jobject nativeHandle,
        jbyteArray bytes,
        jint size
) {
    try {
        auto sampler = reinterpret_cast<Sampler *>(env->GetDirectBufferAddress(nativeHandle));
        if (!sampler) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        jbyte *byteArray = env->GetByteArrayElements(bytes, nullptr);
        if (!byteArray) {
            throw std::runtime_error("Failed to get byte array elements");
        }

        std::vector<uint8_t> samples(
                reinterpret_cast<uint8_t *>(byteArray),
                reinterpret_cast<uint8_t *>(byteArray) + size
        );

        env->ReleaseByteArrayElements(bytes, byteArray, JNI_ABORT);

        sampler->play(samples.data(), samples.size());
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in playNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_stopNative(JNIEnv *env, jobject thisObject, jobject nativeHandle) {
    try {
        auto sampler = reinterpret_cast<Sampler *>(env->GetDirectBufferAddress(nativeHandle));
        if (!sampler) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        sampler->stop();
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in stopNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_closeNative(JNIEnv *env, jobject thisObject, jobject nativeHandle) {
    try {
        auto sampler = reinterpret_cast<Sampler *>(env->GetDirectBufferAddress(nativeHandle));
        if (!sampler) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        delete sampler;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in closeNative method: ") + e.what());
    }
}