#include <memory>
#include "decoder_NativeDecoder.h"
#include "decoder.h"

static jclass exceptionClass;
static jclass formatClass;
static jmethodID formatConstructor;
static jclass frameClass;
static jmethodID frameConstructor;

void handleException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(exceptionClass, ("JNI ERROR: " + errorMessage).c_str());
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) {
        throw std::runtime_error("Failed to get JNI environment");
    }

    exceptionClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/RuntimeException"));
    if (exceptionClass == nullptr) {
        throw std::runtime_error("Failed to find java/lang/RuntimeException class");
    }

    formatClass = (jclass) env->NewGlobalRef(env->FindClass("decoder/NativeFormat"));
    if (formatClass == nullptr) {
        throw std::runtime_error("Failed to find decoder/NativeFormat class");
    }

    frameClass = (jclass) env->NewGlobalRef(env->FindClass("decoder/NativeFrame"));
    if (frameClass == nullptr) {
        throw std::runtime_error("Failed to find decoder/NativeFrame class");
    }

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(Ljava/lang/String;JIIIID)V");
    if (formatConstructor == nullptr) {
        throw std::runtime_error("Failed to find decoder/NativeFormat <init>");
    }

    frameConstructor = env->GetMethodID(frameClass, "<init>", "(IJ[B)V");
    if (frameConstructor == nullptr) {
        throw std::runtime_error("Failed to find decoder/NativeFrame <init>");
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) env->DeleteGlobalRef(exceptionClass);

    if (formatClass) env->DeleteGlobalRef(formatClass);

    if (frameClass) env->DeleteGlobalRef(frameClass);
}

JNIEXPORT jobject JNICALL Java_decoder_NativeDecoder_initializeNative(
        JNIEnv *env,
        jobject obj,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream
) {
    try {
        const char *locationChars = env->GetStringUTFChars(location, nullptr);
        if (!locationChars) {
            throw std::runtime_error("Failed to get location string");
        }

        std::string locationStr(locationChars);

        env->ReleaseStringUTFChars(location, locationChars);

        auto decoder = new Decoder(locationStr.c_str(), findAudioStream, findVideoStream);
        return env->NewDirectByteBuffer(decoder, 0);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in initNative method: ") + e.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL Java_decoder_NativeDecoder_getFormatNative(JNIEnv *env, jobject obj, jobject nativeHandle) {
    try {
        auto decoder = reinterpret_cast<Decoder *>(env->GetDirectBufferAddress(nativeHandle));
        if (!decoder) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        auto format = decoder->format;
        if (!format) {
            throw std::runtime_error("Failed to get format");
        }

        jstring location = env->NewStringUTF(format->location);

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
Java_decoder_NativeDecoder_nextFrameNative(JNIEnv *env, jobject obj, jobject nativeHandle, jint width, jint height) {
    try {
        auto decoder = reinterpret_cast<Decoder *>(env->GetDirectBufferAddress(nativeHandle));
        if (!decoder) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        auto frame = decoder->nextFrame(width, height);
        if (!frame) {
            return nullptr;
        }

        if (frame->bytes.empty()) {
            delete frame;
            return nullptr;
        }

        jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(frame->bytes.size()));
        if (byteArray == nullptr) {
            delete frame;
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

        delete frame;

        return javaObject;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in nextFrameNative method: ") + e.what());
        return nullptr;
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_seekToNative(
        JNIEnv
        *env,
        jobject obj,
        jobject nativeHandle,
        jlong timestampMicros,
        jboolean
        keyframesOnly
) {
    try {
        auto decoder = reinterpret_cast<Decoder *>(env->GetDirectBufferAddress(nativeHandle));
        if (!decoder) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        decoder->seekTo(static_cast<long>(timestampMicros), keyframesOnly);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in seekToNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_resetNative(JNIEnv *env, jobject obj, jobject nativeHandle) {
    try {
        auto decoder = reinterpret_cast<Decoder *>(env->GetDirectBufferAddress(nativeHandle));
        if (!decoder) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        decoder->reset();
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in resetNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_closeNative(JNIEnv *env, jobject obj, jobject nativeHandle) {
    try {
        auto decoder = reinterpret_cast<Decoder *>(env->GetDirectBufferAddress(nativeHandle));
        if (!decoder) {
            throw std::runtime_error("Invalid nativeHandle");
        }

        delete decoder;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in closeNative method: ") + e.what());
    }
}