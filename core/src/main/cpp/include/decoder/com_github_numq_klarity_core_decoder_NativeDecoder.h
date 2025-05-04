#include <jni.h>
#include <shared_mutex>
#include <string>
#include "common.h"
#include "decoder.h"
#include "exception.h"
#include "hwaccel.h"

#ifndef _Included_com_github_numq_klarity_core_decoder_NativeDecoder
#define _Included_com_github_numq_klarity_core_decoder_NativeDecoder
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jintArray
JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_getAvailableHardwareAcceleration(
        JNIEnv *env,
        jclass thisClass
);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_create(
        JNIEnv *env,
        jclass thisClass,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream,
        jboolean decodeAudioStream,
        jboolean decodeVideoStream,
        jintArray hardwareAccelerationCandidates
);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_getFormat(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_decodeAudio(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong buffer,
        jint capacity
);

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_decodeVideo(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong buffer,
        jint capacity
);

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_seekTo(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong timestampMicros,
        jboolean keyframesOnly
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_reset(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
);

#ifdef __cplusplus
}
#endif
#endif