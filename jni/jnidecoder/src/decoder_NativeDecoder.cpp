#include <memory>
#include <shared_mutex>
#include <unordered_map>
#include "decoder_NativeDecoder.h"
#include "decoder.h"

static jclass exceptionClass;
static jclass formatClass;
static jmethodID formatConstructor;
static jclass frameClass;
static jmethodID frameConstructor;
static std::shared_mutex mutex;
static std::unordered_map<jlong, std::shared_ptr<Decoder>> decoders;

void handleException(JNIEnv *env, const std::string &errorMessage) {
    if (exceptionClass) {
        env->ThrowNew(exceptionClass, ("JNI ERROR: " + errorMessage).c_str());
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    exceptionClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/RuntimeException"));
    if (exceptionClass == nullptr) {
        return JNI_ERR;
    }

    formatClass = (jclass) env->NewGlobalRef(env->FindClass("decoder/NativeFormat"));
    if (formatClass == nullptr) {
        return JNI_ERR;
    }

    frameClass = (jclass) env->NewGlobalRef(env->FindClass("decoder/NativeFrame"));
    if (frameClass == nullptr) {
        return JNI_ERR;
    }

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(Ljava/lang/String;JIIIID)V");
    if (formatConstructor == nullptr) {
        return JNI_ERR;
    }

    frameConstructor = env->GetMethodID(frameClass, "<init>", "(IJ[B)V");
    if (frameConstructor == nullptr) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) env->DeleteGlobalRef(exceptionClass);
    if (formatClass) env->DeleteGlobalRef(formatClass);
    if (frameClass) env->DeleteGlobalRef(frameClass);

    decoders.clear();
}

JNIEXPORT jlong JNICALL Java_decoder_NativeDecoder_createNative(
        JNIEnv *env,
        jobject obj,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream
) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    try {
        const char *locationChars = env->GetStringUTFChars(location, nullptr);
        if (!locationChars) {
            throw std::runtime_error("Failed to get location string");
        }

        std::string locationStr(locationChars);
        env->ReleaseStringUTFChars(location, locationChars);

        auto decoder = std::make_shared<Decoder>(locationStr, findAudioStream, findVideoStream);
        if (!decoder) {
            throw std::runtime_error("Failed to create decoder");
        }

        auto handle = reinterpret_cast<jlong>(decoder.get());

        decoders[handle] = std::move(decoder);

        return handle;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in createNative method: ") + e.what());
        return 0;
    }
}

JNIEXPORT jobject JNICALL Java_decoder_NativeDecoder_getFormatNative(JNIEnv *env, jobject obj, jlong handle) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = decoders.find(handle);
        if (it == decoders.end()) {
            throw std::runtime_error("Invalid handle");
        }

        auto format = it->second->format.get();
        if (!format) {
            throw std::runtime_error("Failed to get format");
        }

        jstring location = env->NewStringUTF(format->location.c_str());
        if (!location) {
            throw std::runtime_error("Failed to create location string");
        }

        jobject javaObject = env->NewObject(
                formatClass,
                formatConstructor,
                location,
                static_cast<jlong>(format->durationMicros),
                static_cast<jint>(format->sampleRate),
                static_cast<jint>(format->channels),
                static_cast<jint>(format->width),
                static_cast<jint>(format->height),
                static_cast<jdouble>(format->frameRate)
        );

        return javaObject;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in getFormatNative method: ") + e.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL
Java_decoder_NativeDecoder_nextFrameNative(JNIEnv *env, jobject obj, jlong handle, jint width, jint height) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = decoders.find(handle);
        if (it == decoders.end()) {
            throw std::runtime_error("Invalid handle");
        }

        auto frame = it->second->nextFrame(width, height);
        if (!frame) {
            return nullptr;
        }

        jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(frame->bytes.size()));
        if (byteArray == nullptr) {
            throw std::runtime_error("Failed to allocate byte array for frame data");
        }

        env->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(frame->bytes.size()),
                                reinterpret_cast<const jbyte *>(frame->bytes.data()));

        jobject javaObject = env->NewObject(
                frameClass,
                frameConstructor,
                static_cast<jint>(frame->type),
                static_cast<jlong>(frame->timestampMicros),
                byteArray
        );

        return javaObject;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in nextFrameNative method: ") + e.what());
        return nullptr;
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_seekToNative(
        JNIEnv *env,
        jobject obj,
        jlong handle,
        jlong timestampMicros,
        jboolean keyframesOnly
) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = decoders.find(handle);
        if (it == decoders.end()) {
            throw std::runtime_error("Invalid handle");
        }

        it->second->seekTo(static_cast<long>(timestampMicros), keyframesOnly);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in seekToNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_resetNative(JNIEnv *env, jobject obj, jlong handle) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = decoders.find(handle);
        if (it == decoders.end()) {
            throw std::runtime_error("Invalid handle");
        }

        it->second->reset();
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in resetNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_deleteNative(JNIEnv *env, jobject obj, jlong handle) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    try {
        auto it = decoders.find(handle);
        if (it == decoders.end()) {
            throw std::runtime_error("Invalid handle");
        }

        decoders.erase(it);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in deleteNative method: ") + e.what());
    }
}