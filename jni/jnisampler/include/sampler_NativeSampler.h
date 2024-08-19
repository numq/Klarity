/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class sampler_NativeSampler */

#ifndef _Included_sampler_NativeSampler
#define _Included_sampler_NativeSampler
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     sampler_NativeSampler
 * Method:    setPlaybackSpeedNative
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_setPlaybackSpeedNative
  (JNIEnv *, jobject, jlong, jfloat);

/*
 * Class:     sampler_NativeSampler
 * Method:    setVolumeNative
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_setVolumeNative
  (JNIEnv *, jobject, jlong, jfloat);

/*
 * Class:     sampler_NativeSampler
 * Method:    initializeNative
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_initializeNative
  (JNIEnv *, jobject, jlong, jint, jint);

/*
 * Class:     sampler_NativeSampler
 * Method:    startNative
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_startNative
  (JNIEnv *, jobject, jlong);

/*
 * Class:     sampler_NativeSampler
 * Method:    playNative
 * Signature: (J[BI)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_playNative
  (JNIEnv *, jobject, jlong, jbyteArray, jint);

/*
 * Class:     sampler_NativeSampler
 * Method:    stopNative
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_stopNative
  (JNIEnv *, jobject, jlong);

/*
 * Class:     sampler_NativeSampler
 * Method:    closeNative
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sampler_NativeSampler_closeNative
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif
