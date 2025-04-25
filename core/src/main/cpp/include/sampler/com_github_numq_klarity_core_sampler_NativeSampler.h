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

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_createNative(
        JNIEnv *env,
        jclass thisClass,
        jint sampleRate,
        jint channels
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jfloat factor
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jfloat value
);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_startNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_playNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jlong bufferHandle,
        jint bufferSize
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_pauseNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_stopNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_deleteNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

#ifdef __cplusplus
}
#endif
#endif