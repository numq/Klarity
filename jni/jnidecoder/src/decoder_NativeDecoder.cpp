#include "decoder_NativeDecoder.h"
#include "pool.h"
#include "decoder.h"

static jclass exceptionClass;
static jclass formatClass;
static jmethodID formatConstructor;
static jclass frameClass;
static jmethodID frameConstructor;
static Pool<IDecoder> *pool;

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

    formatClass = (jclass) env->NewGlobalRef(env->FindClass("decoder/NativeFormat"));
    if (formatClass == nullptr) {
        std::cerr << "Failed to find decoder/NativeFormat class" << std::endl;
        return JNI_ERR;
    }

    frameClass = (jclass) env->NewGlobalRef(env->FindClass("decoder/NativeFrame"));
    if (frameClass == nullptr) {
        std::cerr << "Failed to find decoder/NativeFrame class" << std::endl;
        return JNI_ERR;
    }

    formatConstructor = env->GetMethodID(formatClass, "<init>", "(JIIIID)V");
    if (formatConstructor == nullptr) {
        std::cerr << "Failed to find decoder/NativeFormat <init>" << std::endl;
        return JNI_ERR;
    }

    frameConstructor = env->GetMethodID(frameClass, "<init>", "(IJ[B)V");
    if (frameConstructor == nullptr) {
        std::cerr << "Failed to find decoder/NativeFrame <init>" << std::endl;
        return JNI_ERR;
    }

    pool = new Pool<IDecoder>();

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_8) != JNI_OK) return;

    if (exceptionClass) env->DeleteGlobalRef(exceptionClass);

    if (formatClass) env->DeleteGlobalRef(formatClass);

    if (frameClass) env->DeleteGlobalRef(frameClass);

    delete pool;
}

JNIEXPORT jobject JNICALL
Java_decoder_NativeDecoder_initNative(
        JNIEnv *env,
        jobject obj,
        jlong id,
        jstring location,
        jboolean findAudioStream,
        jboolean findVideoStream
) {
    jobject javaObject = nullptr;

    try {
        const char *locationChars = env->GetStringUTFChars(location, nullptr);

        std::string locationStr(locationChars);

        if (pool->create((uint64_t) id, new Decoder(locationStr, (bool) findAudioStream, (bool) findVideoStream))) {

            IDecoder *decoder;

            if ((decoder = pool->acquire((uint64_t) id))) {
                auto format = decoder->format;

                javaObject = env->NewObject(
                        formatClass,
                        formatConstructor,
                        (jlong) format->durationMicros,
                        (jint) format->sampleRate,
                        (jint) format->channels,
                        (jint) format->width,
                        (jint) format->height,
                        (jdouble) format->frameRate
                );

                delete format;
            }
        }

        env->ReleaseStringUTFChars(location, locationChars);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in openNative method: ") + e.what());
    }

    return javaObject;
}

JNIEXPORT jobject JNICALL
Java_decoder_NativeDecoder_nextFrameNative(JNIEnv *env, jobject obj, jlong id) {
    jobject javaObject = nullptr;

    IDecoder *decoder;

    try {
        if ((decoder = pool->acquire((uint64_t) id))) {

            Frame *frame;

            if (!(frame = decoder->nextFrame())) return nullptr;

            jbyteArray byteArray = nullptr;

            if (!frame->bytes.empty()) {
                auto length = (jsize) frame->bytes.size();

                byteArray = env->NewByteArray(length);

                if (byteArray) {
                    env->SetByteArrayRegion(byteArray, 0, length, (jbyte *) frame->bytes.data());
                }
            }

            javaObject = env->NewObject(
                    frameClass,
                    frameConstructor,
                    frame->type,
                    frame->timestampMicros,
                    byteArray
            );

            if (byteArray) env->DeleteLocalRef(byteArray);

            delete frame;
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in readFrameNative method: ") + e.what());
    }

    return javaObject;
}

JNIEXPORT void JNICALL
Java_decoder_NativeDecoder_seekToNative(JNIEnv *env, jobject obj, jlong id, jlong timestampMicros) {
    IDecoder *decoder;

    try {
        if ((decoder = pool->acquire((uint64_t) id))) {
            decoder->seekTo((long) timestampMicros);
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in seekToNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_resetNative(JNIEnv *env, jobject obj, jlong id) {
    IDecoder *decoder;

    try {
        if ((decoder = pool->acquire((uint64_t) id))) {
            decoder->reset();
        }
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in resetNative method: ") + e.what());
    }
}

JNIEXPORT void JNICALL Java_decoder_NativeDecoder_closeNative(JNIEnv *env, jobject obj, jlong id) {
    try {
        pool->release((uint64_t) id);
    } catch (const std::exception &e) {
        handleException(env, std::string("Exception in closeNative method: ") + e.what());
    }
}