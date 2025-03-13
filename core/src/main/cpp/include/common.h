#ifndef KLARITY_COMMON_H
#define KLARITY_COMMON_H

#include "jni.h"
#include "decoder.h"
#include "sampler.h"
#include <unordered_map>
#include <memory>
#include <shared_mutex>

extern std::shared_mutex decoderMutex;

extern std::shared_mutex samplerMutex;

extern std::unordered_map<jlong, std::unique_ptr<Decoder>> decoderPointers;

extern std::unordered_map<jlong, std::unique_ptr<Sampler>> samplerPointers;

extern jclass exceptionClass;

extern jclass formatClass;

extern jmethodID formatConstructor;

extern jclass frameClass;

extern jmethodID frameConstructor;

extern void handleException(JNIEnv *env, const std::string &errorMessage);

extern Decoder *getDecoderPointer(jlong handle);

extern Sampler *getSamplerPointer(jlong handle);

extern void deleteDecoderPointer(jlong handle);

extern void deleteSamplerPointer(jlong handle);

#endif // KLARITY_COMMON_H