#include <jni.h>
#include <shared_mutex>
#include <string>
#include "common.h"
#include "exception.h"
#include "sampler.h"

#ifndef _Included_com_github_numq_klarity_core_sampler_NativeSampler
#define _Included_com_github_numq_klarity_core_sampler_NativeSampler
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_create(
        JNIEnv *env,
        jclass thisClass,
        jint sampleRate,
        jint channels
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_setPlaybackSpeed(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jfloat factor
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_setVolume(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jfloat value
);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_start(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_play(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle,
        jlong bufferHandle,
        jint bufferSize
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_pause(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_stop(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong samplerHandle
);

#ifdef __cplusplus
}
#endif
#endif