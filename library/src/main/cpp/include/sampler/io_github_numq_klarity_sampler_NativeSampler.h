#include <jni.h>
#include <shared_mutex>
#include <string>
#include "common.h"
#include "exception.h"
#include "sampler.h"

#ifndef _Included_io_github_numq_klarity_sampler_NativeSampler
#define _Included_io_github_numq_klarity_sampler_NativeSampler
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_create(
        JNIEnv *env,
        jclass thisClass,
        jint sampleRate,
        jint channels
);

JNIEXPORT jlong JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_start(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_write(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jbyteArray bytes,
        jfloat volume,
        jfloat playbackSpeedFactor
);

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_stop(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_flush(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_drain(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jfloat volume,
        jfloat playbackSpeedFactor
);

JNIEXPORT void JNICALL Java_io_github_numq_klarity_sampler_NativeSampler_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

#ifdef __cplusplus
}
#endif
#endif