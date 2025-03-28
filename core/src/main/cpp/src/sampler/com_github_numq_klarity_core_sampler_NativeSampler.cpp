#include "com_github_numq_klarity_core_sampler_NativeSampler.h"

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_createNative(
        JNIEnv *env,
        jclass thisClass,
        jint sampleRate,
        jint channels
) {
    std::unique_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = std::make_unique<Sampler>(
                static_cast<uint32_t>(sampleRate),
                static_cast<uint32_t>(channels)
        );

        auto handle = reinterpret_cast<jlong>(sampler.get());

        samplerPointers[handle] = std::move(sampler);

        return handle;
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }

    return -1;
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jfloat factor
) {
    std::shared_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = getSamplerPointer(handle);

        sampler->setPlaybackSpeed(static_cast<float>(factor));
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jfloat value
) {
    std::shared_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = getSamplerPointer(handle);

        sampler->setVolume(static_cast<float>(value));
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }
}

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_startNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::shared_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = getSamplerPointer(handle);

        return static_cast<jlong>(sampler->start());
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }

    return -1;
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_playNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jbyteArray bytes,
        jint size
) {
    std::shared_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = getSamplerPointer(handle);

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
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_pauseNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::shared_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = getSamplerPointer(handle);

        sampler->pause();
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_stopNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::shared_lock<std::shared_mutex> lock(samplerMutex);

    try {
        auto sampler = getSamplerPointer(handle);

        sampler->stop();
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_deleteNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::unique_lock<std::shared_mutex> lock(samplerMutex);

    try {
        deleteSamplerPointer(handle);
    } catch (const SamplerException &e) {
        handleSamplerException(env, e.what());
    } catch (const std::exception &e) {
        handleRuntimeException(env, e.what());
    }
}