#include "com_github_numq_klarity_core_decoder_NativeDecoder.h"

JNIEXPORT jintArray
JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getAvailableHardwareAccelerationNative(
        JNIEnv *env,
        jclass thisClass
) {
    return handleException<jintArray>(env, [&] {
        auto hwAccels = HardwareAcceleration::getAvailableHardwareAcceleration();

        auto size = static_cast<jsize>(hwAccels.size());

        auto result = env->NewIntArray(size);

        if (result) {
            env->SetIntArrayRegion(result, 0, size, reinterpret_cast<const jint *>(hwAccels.data()));

            return result;
        }

        return static_cast<jintArray>(nullptr);
    }, nullptr);
}

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_createNative(
        JNIEnv *env,
        jclass thisClass,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream,
        jint hardwareAcceleration
) {
    return handleException<jlong>(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        auto locationChars = env->GetStringUTFChars(location, nullptr);

        if (!locationChars) {
            throw std::runtime_error("Unable to get location string");
        }

        std::string locationStr(locationChars);

        env->ReleaseStringUTFChars(location, locationChars);

        auto decoder = std::make_unique<Decoder>(
                locationStr,
                findAudioStream,
                findVideoStream,
                hardwareAcceleration
        );

        auto handle = reinterpret_cast<jlong>(decoder.get());

        decoderPointers[handle] = std::move(decoder);

        return handle;
    }, -1);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getFormatNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException<jobject>(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        auto decoder = getDecoderPointer(handle);

        auto format = decoder->format;

        auto location = env->NewStringUTF(format.location.c_str());

        if (!location) {
            throw std::runtime_error("Could not create location string");
        }

        auto javaObject = env->NewObject(
                formatClass,
                formatConstructor,
                location,
                static_cast<jlong>(format.durationMicros),
                static_cast<jint>(format.sampleRate),
                static_cast<jint>(format.channels),
                static_cast<jint>(format.width),
                static_cast<jint>(format.height),
                static_cast<jdouble>(format.frameRate)
        );

        env->DeleteLocalRef(location);

        return javaObject;
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_decodeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jint width,
        jint height
) {
    return handleException<jobject>(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        auto decoder = getDecoderPointer(handle);

        auto frame = decoder->decode(width, height);

        if (frame.has_value()) {
            auto size = static_cast<jsize>(frame->bytes.size());

            auto byteArray = env->NewByteArray(size);

            if (!byteArray) {
                throw std::runtime_error("Could not create byte array");
            }

            env->SetByteArrayRegion(
                    byteArray,
                    0,
                    size,
                    reinterpret_cast<const jbyte *>(frame->bytes.data())
            );

            auto javaObject = env->NewObject(
                    frameClass,
                    frameConstructor,
                    static_cast<jint>(frame->type),
                    static_cast<jlong>(frame->timestampMicros),
                    byteArray
            );

            env->DeleteLocalRef(byteArray);

            if (!javaObject) {
                throw std::runtime_error("Could not create frame object");
            }

            return javaObject;
        }

        return static_cast<jobject>(nullptr);
    }, nullptr);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_seekToNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jlong timestampMicros,
        jboolean keyframesOnly
) {
    return handleException(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        auto decoder = getDecoderPointer(handle);

        decoder->seekTo(static_cast<long>(timestampMicros), keyframesOnly);
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_resetNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        auto decoder = getDecoderPointer(handle);

        decoder->reset();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_deleteNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        deleteDecoderPointer(handle);
    });
}