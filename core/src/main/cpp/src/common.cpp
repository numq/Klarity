#include "common.h"

std::shared_mutex decoderMutex;

std::shared_mutex samplerMutex;

std::unordered_map<jlong, std::unique_ptr<Decoder>> decoderPointers;

std::unordered_map<jlong, std::unique_ptr<Sampler>> samplerPointers;

jclass exceptionClass = nullptr;

jclass formatClass = nullptr;

jmethodID formatConstructor = nullptr;

jclass frameClass = nullptr;

jmethodID frameConstructor = nullptr;

void handleException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(exceptionClass, errorMessage.c_str());
}

Decoder *getDecoderPointer(jlong handle) {
    auto it = decoderPointers.find(handle);
    if (it == decoderPointers.end()) {
        throw std::runtime_error("Invalid handle");
    }
    return it->second.get();
}

Sampler *getSamplerPointer(jlong handle) {
    auto it = samplerPointers.find(handle);
    if (it == samplerPointers.end()) {
        throw std::runtime_error("Invalid handle");
    }
    return it->second.get();
}

void deleteDecoderPointer(jlong handle) {
    if (decoderPointers.erase(handle) == 0) {
        throw std::runtime_error("Unable to free native decoder pointer");
    }
}

void deleteSamplerPointer(jlong handle) {
    if (samplerPointers.erase(handle) == 0) {
        throw std::runtime_error("Unable to free native sampler pointer");
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    exceptionClass = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/RuntimeException")));
    if (exceptionClass == nullptr) {
        return JNI_ERR;
    }

    formatClass = (jclass) env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/format/NativeFormat"));
    if (formatClass == nullptr) {
        return JNI_ERR;
    }

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(Ljava/lang/String;JIIIID)V");
    if (formatConstructor == nullptr) {
        return JNI_ERR;
    }

    frameClass = (jclass) env->NewGlobalRef(env->FindClass("com/github/numq/klarity/core/frame/NativeFrame"));
    if (frameClass == nullptr) {
        return JNI_ERR;
    }

    frameConstructor = env->GetMethodID(frameClass, "<init>", "(IJ[B)V");
    if (frameConstructor == nullptr) {
        return JNI_ERR;
    }

    if (Pa_Initialize() != paNoError) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) {
        env->DeleteGlobalRef(exceptionClass);
        exceptionClass = nullptr;
    }

    if (formatClass) {
        env->DeleteGlobalRef(formatClass);
        formatClass = nullptr;
    }

    if (frameClass) {
        env->DeleteGlobalRef(frameClass);
        frameClass = nullptr;
    }

    Pa_Terminate();

    decoderPointers.clear();

    samplerPointers.clear();
}