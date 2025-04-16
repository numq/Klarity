#include "com_github_numq_klarity_core_sampler_NativeSampler.h"

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_createNative(
        JNIEnv *env,
        jclass thisClass,
        jint sampleRate,
        jint channels
) {
    return handleException<jlong>(env, [&] {
        auto sampler = new Sampler(
                static_cast<uint32_t>(sampleRate),
                static_cast<uint32_t>(channels)
        );

        return reinterpret_cast<jlong>(sampler);
    }, -1);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jfloat factor
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(handle);

        sampler->setPlaybackSpeed(static_cast<float>(factor));
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jfloat value
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(handle);

        sampler->setVolume(static_cast<float>(value));
    });
}

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_startNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException<jlong>(env, [&] {
        auto sampler = getSamplerPointer(handle);

        return static_cast<jlong>(sampler->start());
    }, 0);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_playNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jbyteArray bytes
) {
    return handleException(env, [&] {
        auto size = env->GetArrayLength(bytes);

        if (size <= 0) {
            return;
        }

        auto byteArray = env->GetByteArrayElements(bytes, nullptr);

        if (!byteArray) {
            throw std::runtime_error("Could not get byte array elements");
        }

        auto sampler = getSamplerPointer(handle);

        std::vector<uint8_t> samples(
                reinterpret_cast<uint8_t *>(byteArray),
                reinterpret_cast<uint8_t *>(byteArray) + size
        );

        env->ReleaseByteArrayElements(bytes, byteArray, 0);

        sampler->play(samples.data(), samples.size());
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_pauseNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(handle);

        sampler->pause();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_stopNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(handle);

        sampler->stop();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_deleteNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        delete getSamplerPointer(handle);
    });
}