#include <jni.h>
#include <shared_mutex>
#include "common.h"
#include "decoder.h"

#ifndef _Included_com_github_numq_klarity_core_decoder_NativeDecoder
#define _Included_com_github_numq_klarity_core_decoder_NativeDecoder
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_createNative
        (JNIEnv *, jclass, jstring, jboolean, jboolean);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getFormatNative
        (JNIEnv *, jclass, jlong);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_nextFrameNative
        (JNIEnv *, jclass, jlong, jint, jint);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_seekToNative
        (JNIEnv *, jclass, jlong, jlong, jboolean);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_resetNative
        (JNIEnv *, jclass, jlong);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_deleteNative
        (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif