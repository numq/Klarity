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
        jboolean decodeAudioStream,
        jboolean decodeVideoStream,
        jint sampleRate,
        jint channels,
        jint width,
        jint height,
        jintArray hardwareAccelerationCandidates
) {
    return handleException<jlong>(env, [&] {
        auto locationChars = env->GetStringUTFChars(location, nullptr);

        if (!locationChars) {
            throw std::runtime_error("Unable to get location string");
        }

        std::string locationStr(locationChars);

        env->ReleaseStringUTFChars(location, locationChars);

        auto hardwareAccelerationCandidatesSize = env->GetArrayLength(hardwareAccelerationCandidates);

        auto intArray = env->GetIntArrayElements(hardwareAccelerationCandidates, nullptr);

        if (!intArray) {
            throw std::runtime_error("Unable to get hardware acceleration candidates");
        }

        auto candidates = std::vector<uint32_t>(
                intArray,
                intArray + hardwareAccelerationCandidatesSize
        );

        env->ReleaseIntArrayElements(hardwareAccelerationCandidates, intArray, JNI_ABORT);

        auto decoder = new Decoder(
                locationStr,
                findAudioStream,
                findVideoStream,
                decodeAudioStream,
                decodeVideoStream,
                sampleRate,
                channels,
                width,
                height,
                candidates
        );

        return reinterpret_cast<jlong>(decoder);
    }, -1);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_getFormatNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(handle);

        auto format = decoder->format;

        auto location = env->NewStringUTF(format.location.c_str());

        if (!location) {
            throw std::runtime_error("Could not create location string");
        }

        auto formatObject = env->NewObject(
                formatClass,
                formatConstructor,
                location,
                static_cast<jlong>(format.durationMicros),
                static_cast<jint>(format.sampleRate),
                static_cast<jint>(format.channels),
                static_cast<jint>(format.width),
                static_cast<jint>(format.height),
                static_cast<jdouble>(format.frameRate),
                static_cast<jint>(format.hwDeviceType)
        );

        env->DeleteLocalRef(location);

        return formatObject;
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_decodeAudioNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(handle);

        auto frame = decoder->decodeAudio().release();

        if (!frame) {
            return static_cast<jobject>(nullptr);
        }

        return env->NewObject(
                frameClass,
                frameConstructor,
                frame->buffer,
                frame->size,
                static_cast<jlong>(frame->timestampMicros)
        );
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_decodeVideoNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(handle);

        auto frame = decoder->decodeVideo();

        if (!frame) {
            return static_cast<jobject>(nullptr);
        }

        return env->NewObject(
                frameClass,
                frameConstructor,
                frame->buffer,
                frame->size,
                static_cast<jlong>(frame->timestampMicros)
        );
    }, nullptr);
}

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_seekToNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jlong timestampMicros,
        jboolean keyframesOnly
) {
    return handleException<jlong>(env, [&] {
        auto decoder = getDecoderPointer(handle);

        return static_cast<jlong>(decoder->seekTo(static_cast<long>(timestampMicros), keyframesOnly));
    }, -1);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_resetNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
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
        delete getDecoderPointer(handle);
    });
}