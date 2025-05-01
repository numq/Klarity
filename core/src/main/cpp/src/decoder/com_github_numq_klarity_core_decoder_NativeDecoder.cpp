#include "com_github_numq_klarity_core_decoder_NativeDecoder.h"

JNIEXPORT jintArray
JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_getAvailableHardwareAcceleration(
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

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_create(
        JNIEnv *env,
        jclass thisClass,
        jstring location,
        jint audioFramePoolCapacity,
        jint videoFramePoolCapacity,
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
                audioFramePoolCapacity,
                videoFramePoolCapacity,
                sampleRate,
                channels,
                width,
                height,
                candidates
        );

        return reinterpret_cast<jlong>(decoder);
    }, -1);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_getFormat(
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
                static_cast<jint>(format.hwDeviceType)
        );

        env->DeleteLocalRef(location);

        return formatObject;
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_decodeAudio(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        auto frame = decoder->decodeAudio().release();

        if (!frame) {
            return static_cast<jobject>(nullptr);
        }

        return env->NewObject(
                frameClass,
                frameConstructor,
                reinterpret_cast<jlong>(frame->buffer),
                static_cast<jint>(frame->size),
                static_cast<jlong>(frame->timestampMicros),
                static_cast<jint>(frame->type)
        );
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_decodeVideo(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException<jobject>(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        auto frame = decoder->decodeVideo().release();

        if (!frame) {
            return static_cast<jobject>(nullptr);
        }

        return env->NewObject(
                frameClass,
                frameConstructor,
                reinterpret_cast<jlong>(frame->buffer),
                static_cast<jint>(frame->size),
                static_cast<jlong>(frame->timestampMicros),
                static_cast<jint>(frame->type)
        );
    }, nullptr);
}

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_seekTo(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong timestampMicros,
        jboolean keyframesOnly
) {
    return handleException<jlong>(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        return static_cast<jlong>(decoder->seekTo(static_cast<long>(timestampMicros), keyframesOnly));
    }, -1);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_reset(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        decoder->reset();
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_delete(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle
) {
    return handleException(env, [&] {
        delete getDecoderPointer(decoderHandle);
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_releaseAudioBuffer(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong bufferHandle
) {
    return handleException(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        decoder->releaseAudioBuffer(reinterpret_cast<void *>(bufferHandle));
    });
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_00024Native_releaseVideoBuffer(
        JNIEnv *env,
        jclass thisClass,
        jlong decoderHandle,
        jlong bufferHandle
) {
    return handleException(env, [&] {
        auto decoder = getDecoderPointer(decoderHandle);

        decoder->releaseVideoBuffer(reinterpret_cast<void *>(bufferHandle));
    });
}