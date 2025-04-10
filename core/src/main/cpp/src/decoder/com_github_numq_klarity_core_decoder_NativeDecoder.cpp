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
        jdouble frameRate,
        jintArray hardwareAccelerationCandidates
) {
    return handleException<jlong>(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        auto locationChars = env->GetStringUTFChars(location, nullptr);

        if (!locationChars) {
            throw std::runtime_error("Unable to get location string");
        }

        std::string locationStr(locationChars);

        env->ReleaseStringUTFChars(location, locationChars);

        std::optional<AudioParameters> audioParameters = std::nullopt;

        if (findAudioStream) {
            audioParameters = AudioParameters{};

            if (decodeAudioStream) {
                audioParameters->decodingParameters = AudioParameters::DecodingParameters{};

                audioParameters->decodingParameters->sampleRate =
                        sampleRate > 0 ? std::make_optional(sampleRate) : std::nullopt;

                audioParameters->decodingParameters->channels =
                        channels > 0 ? std::make_optional(channels) : std::nullopt;
            }
        }

        std::optional<VideoParameters> videoParameters = std::nullopt;

        if (findVideoStream) {
            videoParameters = VideoParameters{};

            if (decodeVideoStream) {
                videoParameters->decodingParameters = VideoParameters::DecodingParameters{};

                videoParameters->decodingParameters->width = width > 0 ? std::make_optional(width) : std::nullopt;

                videoParameters->decodingParameters->height = height > 0 ? std::make_optional(height) : std::nullopt;

                auto hardwareAccelerationCandidatesSize = env->GetArrayLength(hardwareAccelerationCandidates);

                if (hardwareAccelerationCandidatesSize > 0) {
                    auto intArray = env->GetIntArrayElements(hardwareAccelerationCandidates, nullptr);

                    if (!intArray) {
                        throw std::runtime_error("Unable to get hardware acceleration candidates");
                    }

                    auto candidates = std::vector<uint32_t>(
                            intArray,
                            intArray + hardwareAccelerationCandidatesSize
                    );

                    env->ReleaseIntArrayElements(hardwareAccelerationCandidates, intArray, JNI_ABORT);

                    videoParameters->decodingParameters->hardwareAccelerationCandidates = candidates;
                }
            }
        }

        auto decoder = std::make_unique<Decoder>(
                locationStr,
                audioParameters,
                videoParameters
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
                static_cast<jdouble>(format.frameRate),
                static_cast<jint>(format.hwDeviceType),
                static_cast<jint>(format.audioBufferSize),
                static_cast<jint>(format.videoBufferSize)
        );

        env->DeleteLocalRef(location);

        return javaObject;
    }, nullptr);
}

JNIEXPORT jobject JNICALL Java_com_github_numq_klarity_core_decoder_NativeDecoder_decodeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle,
        jobject byteBuffer
) {
    return handleException<jobject>(env, [&] {
        std::unique_lock<std::shared_mutex> lock(decoderMutex);

        if (!byteBuffer) {
            throw std::runtime_error("Provided byte buffer is null");
        }

        auto buffer = env->GetDirectBufferAddress(byteBuffer);

        if (!buffer) {
            throw std::runtime_error("Could not get direct buffer address");
        }

        auto size = env->GetDirectBufferCapacity(byteBuffer);

        if (size <= 0) {
            throw std::runtime_error("Invalid buffer size");
        }

        auto decoder = getDecoderPointer(handle);

        auto frame = decoder->decode(static_cast<uint8_t *>(buffer), size);

        if (!frame) {
            return static_cast<jobject>(nullptr);
        }

        return env->NewObject(
                frameClass,
                frameConstructor,
                static_cast<jint>(frame->type),
                static_cast<jlong>(frame->timestampMicros)
        );
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