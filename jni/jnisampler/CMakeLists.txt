cmake_minimum_required(VERSION 3.24)
project(jnisampler VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 23)

add_library(jnisampler SHARED src/sampler_NativeSampler.cpp)

target_include_directories(jnisampler PRIVATE include)

# JNI

find_package(JNI)

if (JNI_FOUND)
    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message(STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
    target_include_directories(jnisampler PRIVATE ${JNI_INCLUDE_DIRS})
else ()
    message(FATAL_ERROR "JNI not found.")
endif ()

# OPENAL

SET(OPENAL_INCLUDE_PATH "include/openal")
target_include_directories(jnisampler PRIVATE ${OPENAL_INCLUDE_PATH})

SET(OPENAL_BIN_PATH "bin/openal")
target_link_directories(jnisampler PRIVATE ${OPENAL_BIN_PATH})

target_link_libraries(jnisampler PRIVATE soft_oal)

# KLARITY_SAMPLER

SET(KLARITY_SAMPLER_INCLUDE_PATH "include/klarity_sampler")
target_include_directories(jnisampler PRIVATE ${KLARITY_SAMPLER_INCLUDE_PATH})

SET(KLARITY_SAMPLER_BIN_PATH "bin/klarity_sampler")
target_link_directories(jnisampler PRIVATE ${KLARITY_SAMPLER_BIN_PATH})

target_link_libraries(jnisampler PRIVATE libklarity_sampler)