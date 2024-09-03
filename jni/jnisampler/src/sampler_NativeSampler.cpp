#include <iostream>
#include <shared_mutex>
#include <mutex>
#include "sampler_NativeSampler.h"
#include "klarity_sampler/sampler.h"

static jclass exceptionClass;
static std::shared_mutex mutex;
static std::unordered_map<jlong, std::shared_ptr<Sampler>> samplers;

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

    samplers.clear();

    PaError err = Pa_Terminate();
    if (err != paNoError) {
        throw SamplerException("Failed to terminate PortAudio: " + std::string(Pa_GetErrorText(err)));
    }
}

JNIEXPORT jlong JNICALL Java_sampler_NativeSampler_createNative(
        JNIEnv *env,
        jobject thisObject,
        jint sampleRate,
        jint channels
) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    try {
        auto sampler = std::make_shared<Sampler>(static_cast<uint32_t>(sampleRate), static_cast<uint32_t>(channels));
        if (!sampler) {
            throw std::runtime_error("Failed to create sampler");
        }

        auto handle = reinterpret_cast<jlong>(sampler.get());

        samplers[handle] = std::move(sampler);

        return handle;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in initNative method: ") + e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jobject thisObject,
        jlong handle,
        jfloat factor
) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = samplers.find(handle);
        if (it == samplers.end()) {
            throw std::runtime_error("Invalid handle");
        }

        it->second->setPlaybackSpeed(static_cast<float>(factor));
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setPlaybackSpeedNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jobject thisObject,
        jlong handle,
        jfloat value
) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = samplers.find(handle);
        if (it == samplers.end()) {
            throw std::runtime_error("Invalid handle");
        }

        it->second->setVolume(static_cast<float>(value));
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setVolumeNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_startNative(JNIEnv *env, jobject thisObject, jlong handle) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = samplers.find(handle);
        if (it == samplers.end()) {
            throw std::runtime_error("Invalid handle");
        }

        it->second->start();
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in startNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_playNative(
        JNIEnv *env,
        jobject thisObject,
        jlong handle,
        jbyteArray bytes,
        jint size
) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = samplers.find(handle);
        if (it == samplers.end()) {
            throw std::runtime_error("Invalid handle");
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

        it->second->play(samples.data(), samples.size());
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in playNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_stopNative(JNIEnv *env, jobject thisObject, jlong handle) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = samplers.find(handle);
        if (it == samplers.end()) {
            throw std::runtime_error("Invalid handle");
        }

        it->second->stop();
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in stopNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_deleteNative(JNIEnv *env, jobject thisObject, jlong handle) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = samplers.find(handle);
        if (it == samplers.end()) {
            throw std::runtime_error("Invalid handle");
        }

        samplers.erase(it);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in closeNative method: ") + e.what());
    }
}