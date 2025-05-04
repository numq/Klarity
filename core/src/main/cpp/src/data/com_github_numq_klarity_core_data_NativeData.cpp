#include "com_github_numq_klarity_core_data_NativeData.h"

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_data_NativeData_00024Native_allocate(
        JNIEnv *env,
        jclass thisClass,
        jint capacity
) {
    return handleException<jlong>(env, [&] {
        auto handle = malloc(capacity);

        if (!handle) {
            throw std::runtime_error("Could not allocate native data");
        }

        return reinterpret_cast<jlong>(handle);
    }, -1);
}

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_data_NativeData_00024Native_free(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        free(reinterpret_cast<void *>(handle));
    });
}