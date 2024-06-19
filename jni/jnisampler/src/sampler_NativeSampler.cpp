#include <iostream>
#include <memory>
#include "sampler_NativeSampler.h"
#include "klarity_sampler/sampler.h"

static jclass exceptionClass;
static jclass audioClass;
static jmethodID audioConstructor;
static std::unique_ptr<ISampler> sampler;

void handleException(JNIEnv *env, const std::string &errorMessage) {
    env->ThrowNew(exceptionClass, errorMessage.c_str());
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) {
        std::cerr << "Failed to get JNI environment" << std::endl;
        return JNI_ERR;
    }

    exceptionClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/RuntimeException"));
    if (exceptionClass == nullptr) {
        std::cerr << "Failed to find java/lang/RuntimeException class" << std::endl;
        return JNI_ERR;
    }

    audioClass = (jclass) env->NewGlobalRef(env->FindClass("sampler/NativeSampler"));
    if (audioClass == nullptr) {
        std::cerr << "Failed to find sampler/NativeSampler class" << std::endl;
        return JNI_ERR;
    }

    audioConstructor = env->GetMethodID(audioClass, "<init>", "()V");
    if (audioConstructor == nullptr) {
        std::cerr << "Failed to find sampler/NativeSampler <init>" << std::endl;
        return JNI_ERR;
    }

    sampler = std::make_unique<Sampler>();

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) env->DeleteGlobalRef(exceptionClass);

    if (audioClass) env->DeleteGlobalRef(audioClass);

    sampler.reset();
}

JNIEXPORT jfloat JNICALL Java_sampler_NativeSampler_getCurrentTimeNative(JNIEnv *env, jobject thisObject, jlong id) {
    try {
        return sampler->getCurrentTime(id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in getCurrentTimeNative method: ") + e.what());
    }
    return 0;
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jfloat factor
) {
    try {
        sampler->setPlaybackSpeed(id, (float) factor);
        return true;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setPlaybackSpeedNative method: ") + e.what());
    }
    return false;
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jfloat value
) {
    try {
        sampler->setVolume(id, (float) value);
        return true;
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setVolumeNative method: ") + e.what());
    }
    return false;
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_initNative(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jint sampleRate,
        jint channels,
        jint numBuffers
) {
    try {
        return sampler->initialize(id, (uint32_t) sampleRate, (uint32_t) channels, (uint32_t) numBuffers);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in initNative method: ") + e.what());
    }
    return false;
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_playNative(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jbyteArray bytes,
        jint size
) {
    try {
        jbyte *byteArray = env->GetByteArrayElements(bytes, nullptr);
        if (!byteArray) {
            std::cerr << "Failed to get byte array elements." << std::endl;
            return false;
        }

        std::vector<uint8_t> samples(
                reinterpret_cast<uint8_t *>(byteArray),
                reinterpret_cast<uint8_t *>(byteArray) + size
        );

        env->ReleaseByteArrayElements(bytes, byteArray, 0);

        return sampler->play(id, samples.data(), samples.size());
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in playNative method: ") + e.what());
    }
    return false;
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_pauseNative(JNIEnv *env, jobject thisObject, jlong id) {
    try {
        sampler->pause(id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in pauseNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_resumeNative(JNIEnv *env, jobject thisObject, jlong id) {
    try {
        sampler->resume(id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in resumeNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_stopNative(JNIEnv *env, jobject thisObject, jlong id) {
    try {
        sampler->stop(id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in stopNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_closeNative(JNIEnv *env, jobject thisObject, jlong id) {
    try {
        sampler->close(id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in closeNative method: ") + e.what());
    }
}