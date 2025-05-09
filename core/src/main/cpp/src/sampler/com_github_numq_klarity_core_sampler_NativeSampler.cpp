#include "com_github_numq_klarity_core_sampler_NativeSampler.h"

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_create(
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

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_setPlaybackSpeed(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jfloat factor
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->setPlaybackSpeed(static_cast<float>(factor));
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_setVolume(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jfloat value
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->setVolume(static_cast<float>(value));
    });
}

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_start(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException<jlong>(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        return static_cast<jlong>(sampler->start());
    }, 0);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_write(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jbyteArray bytes
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        auto size = env->GetArrayLength(bytes);

        std::vector<uint8_t> buffer(size);

        env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(buffer.data()));

        sampler->write(buffer.data(), static_cast<int>(buffer.size()));
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_stop(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->stop();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_flush(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->flush();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_drain(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->drain();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        delete getSamplerPointer(samplerHandle);
    });
}