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

extern Decoder *getDecoderPointer(jlong handle);

extern Sampler *getSamplerPointer(jlong handle);

extern void deleteDecoderPointer(jlong handle);

extern void deleteSamplerPointer(jlong handle);

inline void handleException(JNIEnv *env, const std::function<void()> &call) {
    try {
        call();
    } catch (const SamplerException &e) {
        env->ThrowNew(samplerExceptionClass, e.what());
    } catch (const DecoderException &e) {
        env->ThrowNew(decoderExceptionClass, e.what());
    } catch (const HardwareAccelerationException &e) {
        env->ThrowNew(hardwareAccelerationExceptionClass, e.what());
    } catch (const std::bad_alloc &e) {
        env->ThrowNew(runtimeExceptionClass, "Memory allocation failed");
    } catch (const std::exception &e) {
        env->ThrowNew(runtimeExceptionClass, e.what());
    } catch (...) {
        env->ThrowNew(runtimeExceptionClass, "Unexpected native exception");
    }
}

template<typename T>
inline T handleException(JNIEnv *env, const std::function<T()> &call, T defaultValue) {
    try {
        return call();
    } catch (const SamplerException &e) {
        env->ThrowNew(samplerExceptionClass, e.what());
    } catch (const DecoderException &e) {
        env->ThrowNew(decoderExceptionClass, e.what());
    } catch (const HardwareAccelerationException &e) {
        env->ThrowNew(hardwareAccelerationExceptionClass, e.what());
    } catch (const std::bad_alloc &e) {
        env->ThrowNew(runtimeExceptionClass, "Memory allocation failed");
    } catch (const std::exception &e) {
        env->ThrowNew(runtimeExceptionClass, e.what());
    } catch (...) {
        env->ThrowNew(runtimeExceptionClass, "Unexpected native exception");
    }

    return defaultValue;
}

#endif // KLARITY_COMMON_H