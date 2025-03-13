#include "com_github_numq_klarity_core_decoder_NativeDecoder.h"

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_createNative(
        JNIEnv *env,
        jclass thisClass,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream
) {
    std::unique_lock<std::shared_mutex> lock(decoderMutex);

    try {
        const char *locationChars = env->GetStringUTFChars(location, nullptr);

        if (!locationChars) {
            throw std::runtime_error("Failed to get location string");
        }

        std::string locationStr(locationChars);

        env->ReleaseStringUTFChars(location, locationChars);

        auto decoder = std::make_unique<Decoder>(
                locationStr,
                findAudioStream,
                findVideoStream
        );

        auto handle = reinterpret_cast<jlong>(decoder.get());

        decoderPointers[handle] = std::move(decoder);

        return handle;
    } catch (const std::exception &e) {
        handleException(env, e.what());

        return -1;
    }
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getFormatNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::shared_lock<std::shared_mutex> lock(decoderMutex);

    try {
        auto decoder = getDecoderPointer(handle);

        auto format = decoder->format.get();

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

        env->DeleteLocalRef(location);

        return javaObject;
    } catch (const std::exception &e) {
        handleException(env, e.what());

        return nullptr;
    }
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_nextFrameNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jint width,
        jint height
) {
    std::shared_lock<std::shared_mutex> lock(decoderMutex);

    try {
        auto decoder = getDecoderPointer(handle);

        auto frame = decoder->nextFrame(width, height);

        if (!frame) {
            return nullptr;
        }

        jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(frame->bytes.size()));

        if (byteArray == nullptr) {
            throw std::runtime_error("Failed to allocate byte array for frame data");
        }

        env->SetByteArrayRegion(
                byteArray,
                0,
                static_cast<jsize>(frame->bytes.size()),
                reinterpret_cast<const jbyte *>(frame->bytes.data())
        );

        jobject javaObject = env->NewObject(
                frameClass,
                frameConstructor,
                static_cast<jint>(frame->type),
                static_cast<jlong>(frame->timestampMicros),
                byteArray
        );

        env->DeleteLocalRef(byteArray);

        return javaObject;
    } catch (const std::exception &e) {
        handleException(env, e.what());

        return nullptr;
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_seekToNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jlong timestampMicros,
        jboolean keyframesOnly
) {
    std::shared_lock<std::shared_mutex> lock(decoderMutex);

    try {
        auto decoder = getDecoderPointer(handle);

        decoder->seekTo(static_cast<long>(timestampMicros), keyframesOnly);
    } catch (const std::exception &e) {
        handleException(env, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_resetNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::shared_lock<std::shared_mutex> lock(decoderMutex);

    try {
        auto decoder = getDecoderPointer(handle);

        decoder->reset();
    } catch (const std::exception &e) {
        handleException(env, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_deleteNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    std::unique_lock<std::shared_mutex> lock(decoderMutex);

    try {
        deleteDecoderPointer(handle);
    } catch (const std::exception &e) {
        handleException(env, e.what());
    }
}