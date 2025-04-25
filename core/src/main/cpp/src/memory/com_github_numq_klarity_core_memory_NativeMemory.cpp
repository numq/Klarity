#include "com_github_numq_klarity_core_memory_NativeMemory.h"

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_memory_NativeMemory_freeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
) {
    return handleException(env, [&] {
        if (handle > 0) {
            free(reinterpret_cast<void *>(handle));
        }
    });
}