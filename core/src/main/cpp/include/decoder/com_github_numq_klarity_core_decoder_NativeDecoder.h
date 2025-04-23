#include <jni.h>
#include <shared_mutex>
#include <string>
#include "common.h"
#include "exception.h"
#include "decoder.h"
#include "hwaccel.h"

#ifndef _Included_com_github_numq_klarity_core_decoder_NativeDecoder
#define _Included_com_github_numq_klarity_core_decoder_NativeDecoder
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jintArray
JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getAvailableHardwareAccelerationNative(
        JNIEnv *env,
        jclass thisClass
);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_createNative(
        JNIEnv *env,
        jclass thisClass,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream,
        jboolean decodeAudioStream,
        jboolean decodeVideoStream,
        jint sampleRate,
        jint channels,
        jint width,
        jint height,
        jintArray hardwareAccelerationCandidates
);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getFormatNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_decodeAudioNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_decodeVideoNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jobject byteBuffer
);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_seekToNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jlong timestampMicros,
        jboolean keyframesOnly
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_resetNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_deleteNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

#ifdef __cplusplus
}
#endif
#endif