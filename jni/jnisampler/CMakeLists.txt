cmake_minimum_required(VERSION 3.24)
project(jni_sampler VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 23)

add_library(jni_sampler SHARED src/com_github_numq_klarity_core_sampler_NativeSampler.cpp)

target_include_directories(jni_sampler PRIVATE include)

# JNI

find_package(JNI)

if (JNI_FOUND)
    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message(STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
    target_include_directories(jni_sampler PRIVATE ${JNI_INCLUDE_DIRS})
else ()
    message(FATAL_ERROR "JNI not found.")
endif ()

# PORTAUDIO

SET(PORTAUDIO_INCLUDE_PATH "include/portaudio")
target_include_directories(jni_sampler PRIVATE ${PORTAUDIO_INCLUDE_PATH})

SET(PORTAUDIO_BIN_PATH "bin/portaudio")
target_link_directories(jni_sampler PRIVATE ${PORTAUDIO_BIN_PATH})

target_link_libraries(jni_sampler PRIVATE libportaudio)

# KLARITY_SAMPLER

SET(KLARITY_SAMPLER_INCLUDE_PATH "include/klarity_sampler")
target_include_directories(jni_sampler PRIVATE ${KLARITY_SAMPLER_INCLUDE_PATH})

SET(KLARITY_SAMPLER_BIN_PATH "bin/klarity_sampler")
target_link_directories(jni_sampler PRIVATE ${KLARITY_SAMPLER_BIN_PATH})

target_link_libraries(jni_sampler PRIVATE libklarity_sampler)