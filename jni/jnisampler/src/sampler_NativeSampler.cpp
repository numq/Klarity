#include <iostream>
#include "sampler_NativeSampler.h"
#include "pool.h"
#include "klarity_sampler/sampler.h"

static jclass exceptionClass;
static jclass audioClass;
static jmethodID audioConstructor;
static Pool<ISampler> *pool;

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

    pool = new Pool<ISampler>();

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) env->DeleteGlobalRef(exceptionClass);

    if (audioClass) env->DeleteGlobalRef(audioClass);

    delete pool;
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_initNative__JIII(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jint bitsPerSample,
        jint sampleRate,
        jint channels
) {
    try {
        return pool->create(
                id,
                new Sampler(
                        (uint32_t) bitsPerSample,
                        (uint32_t) sampleRate,
                        (uint32_t) channels
                )
        );
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in initNative method: ") + e.what());
        return false;
    }
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_initNative__JIIII(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jint bitsPerSample,
        jint sampleRate,
        jint channels,
        jint numBuffers
) {
    try {
        return pool->create(
                id,
                new Sampler(
                        (uint32_t) bitsPerSample,
                        (uint32_t) sampleRate,
                        (uint32_t) channels,
                        (uint32_t) numBuffers
                )
        );
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in initNative method: ") + e.what());
        return false;
    }
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_setPlaybackSpeedNative(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jfloat factor
) {
    ISampler *sampler;

    try {
        if ((sampler = pool->acquire(id))) {
            sampler->setPlaybackSpeed((float) factor);

            return true;
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setPlaybackSpeedNative method: ") + e.what());
        return false;
    }

    return false;
}

JNIEXPORT jboolean JNICALL Java_sampler_NativeSampler_setVolumeNative(
        JNIEnv *env,
        jobject thisObject,
        jlong id,
        jfloat value
) {
    ISampler *sampler;

    try {
        if ((sampler = pool->acquire(id))) {
            return sampler->setVolume((float) value);
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in setVolumeNative method: ") + e.what());
        return false;
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
    ISampler *sampler;

    try {
        if ((sampler = pool->acquire(id))) {
            jbyte *byteArray = env->GetByteArrayElements(bytes, nullptr);
            if (!byteArray) {
                std::cerr << "Failed to get byte array elements." << std::endl;
                return false;
            }

            std::vector<uint8_t> samples(
                    reinterpret_cast<uint8_t *>(byteArray),
                    reinterpret_cast<uint8_t *>(byteArray) + size
            );

            env->ReleaseByteArrayElements(bytes, byteArray, JNI_ABORT);

            return sampler->play(samples.data(), samples.size());
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in playNative method: ") + e.what());
        return false;
    }

    return false;
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_pauseNative(JNIEnv *env, jobject thisObject, jlong id) {
    ISampler *sampler;

    try {
        if ((sampler = pool->acquire(id))) {
            sampler->pause();
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in pauseNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_resumeNative(JNIEnv *env, jobject thisObject, jlong id) {
    ISampler *sampler;

    try {
        if ((sampler = pool->acquire(id))) {
            sampler->resume();
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in resumeNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_stopNative(JNIEnv *env, jobject thisObject, jlong id) {
    ISampler *sampler;

    try {
        if ((sampler = pool->acquire(id))) {
            sampler->stop();
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in stopNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_sampler_NativeSampler_closeNative(JNIEnv *env, jobject thisObject, jlong id) {
    try {
        pool->release(id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in closeNative method: ") + e.what());
    }
}