#include <jni.h>

#ifndef _Included_com_github_numq_klarity_core_sampler_NativeSampler
#define _Included_com_github_numq_klarity_core_sampler_NativeSampler
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_createNative
        (JNIEnv *, jclass, jint, jint);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setPlaybackSpeedNative
        (JNIEnv *, jclass, jlong, jfloat);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_setVolumeNative
        (JNIEnv *, jclass, jlong, jfloat);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_startNative
        (JNIEnv *, jclass, jlong);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_playNative
        (JNIEnv *, jclass, jlong, jbyteArray, jint);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_stopNative
        (JNIEnv *, jclass, jlong);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_sampler_NativeSampler_deleteNative
        (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
