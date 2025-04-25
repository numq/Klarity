#include <jni.h>
#include "common.h"
#include "exception.h"

#ifndef _Included_com_github_numq_klarity_core_memory_NativeMemory
#define _Included_com_github_numq_klarity_core_memory_NativeMemory
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_memory_NativeMemory_freeNative(
        JNIEnv *env,
        jclass thisClass,
        jlong handle
);

#ifdef __cplusplus
}
#endif
#endif