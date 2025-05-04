#include <jni.h>
#include "common.h"
#include "exception.h"

#ifndef _Included_com_github_numq_klarity_core_data_NativeData
#define _Included_com_github_numq_klarity_core_data_NativeData
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_com_github_numq_klarity_core_data_NativeData_00024Native_allocate(
        JNIEnv *env,
        jclass thisClass,
        jint capacity
);

JNIEXPORT void JNICALL Java_com_github_numq_klarity_core_data_NativeData_00024Native_free(
        JNIEnv *env,
        jclass thisClass,
        jlong pointer
);

#ifdef __cplusplus
}
#endif
#endif