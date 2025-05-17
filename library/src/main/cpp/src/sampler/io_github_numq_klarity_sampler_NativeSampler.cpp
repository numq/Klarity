#include "io_github_numq_klarity_sampler_NativeSampler.h"

JNIEXPORT jlong JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_create(
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

JNIEXPORT jlong JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_start(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException<jlong>(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        return static_cast<jlong>(sampler->start());
    }, 0);
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_write(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jbyteArray bytes,
        jfloat volume,
        jfloat playbackSpeedFactor
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        auto size = env->GetArrayLength(bytes);

        std::vector<uint8_t> buffer(size);

        env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(buffer.data()));

        sampler->write(buffer.data(), static_cast<int>(buffer.size()), volume, playbackSpeedFactor);
    });
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_stop(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->stop();
    });
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_flush(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->flush();
    });
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_drain(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jfloat volume,
        jfloat playbackSpeedFactor
) {
    return handleException(env, [&] {
        auto sampler = getSamplerPointer(samplerHandle);

        sampler->drain(volume, playbackSpeedFactor);
    });
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
) {
    return handleException(env, [&] {
        delete getSamplerPointer(samplerHandle);
    });
}