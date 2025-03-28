#ifndef KLARITY_COMMON_H
#define KLARITY_COMMON_H

#include <jni.h>
#include <memory>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include "decoder.h"
#include "hwaccel.h"
#include "sampler.h"

extern std::shared_mutex decoderMutex;

extern std::shared_mutex samplerMutex;

extern std::unordered_map<jlong, std::unique_ptr<Decoder>> decoderPointers;

extern std::unordered_map<jlong, std::unique_ptr<Sampler>> samplerPointers;

extern jclass runtimeExceptionClass;

extern jclass decoderExceptionClass;

extern jclass hardwareAccelerationExceptionClass;

extern jclass samplerExceptionClass;

extern jclass formatClass;

extern jmethodID formatConstructor;

extern jclass frameClass;

extern jmethodID frameConstructor;

extern void handleRuntimeException(JNIEnv *env, const std::string &errorMessage);

extern void handleDecoderException(JNIEnv *env, const std::string &errorMessage);

extern void handleHardwareAccelerationException(JNIEnv *env, const std::string &errorMessage);

extern void handleSamplerException(JNIEnv *env, const std::string &errorMessage);

extern Decoder *getDecoderPointer(jlong handle);

extern Sampler *getSamplerPointer(jlong handle);

extern void deleteDecoderPointer(jlong handle);

extern void deleteSamplerPointer(jlong handle);

#endif // KLARITY_COMMON_H