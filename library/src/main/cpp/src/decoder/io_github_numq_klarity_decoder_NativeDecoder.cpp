#include "io_github_numq_klarity_decoder_NativeDecoder.h"

JNIEXPORT jintArray
JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_getAvailableHardwareAcceleration(
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

JNIEXPORT jlong JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_create(
        JNIEnv *env,
        jclass thisClass,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream,
        jboolean decodeAudioStream,
        jboolean decodeVideoStream,
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
                candidates
        );

        return reinterpret_cast<jlong>(decoder);
    }, -1);
}

JNIEXPORT jobject JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_getFormat(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

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
                static_cast<jint>(format.hwDeviceType),
                static_cast<jint>(format.videoBufferCapacity)
        );

        env->DeleteLocalRef(location);

        return formatObject;
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_decodeAudio(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        auto frame = decoder->decodeAudio();

        if (!frame.has_value()) {
            return static_cast<jobject>(nullptr);
        }

        auto bytes = frame->bytes;

        auto len = static_cast<jsize>(bytes.size());

        auto byteArray = env->NewByteArray(len);

        env->SetByteArrayRegion(byteArray, 0, len, reinterpret_cast<const jbyte *>(bytes.data()));

        return env->NewObject(
                audioFrameClass,
                audioFrameConstructor,
                byteArray,
                static_cast<jlong>(frame->timestampMicros)
        );
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_decodeVideo(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong buffer,
        jint capacity
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        auto frame = decoder->decodeVideo(reinterpret_cast<uint8_t *>(buffer), capacity);

        if (!frame.has_value()) {
            return static_cast<jobject>(nullptr);
        }

        return env->NewObject(
                videoFrameClass,
                videoFrameConstructor,
                static_cast<jint>(frame->remaining),
                static_cast<jlong>(frame->timestampMicros)
        );
    }, nullptr);
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_seekTo(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong timestampMicros,
        jboolean keyFramesOnly
) {
    return handleException(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        decoder->seekTo(static_cast<long>(timestampMicros), keyFramesOnly);
    });
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_reset(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        decoder->reset();
    });
}

JNIEXPORT void JNICALL Java_io_github_numq_klarity_decoder_NativeDecoder_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException(env, [&] {
        delete getDecoderPointer(decoderHandle);
    });
}