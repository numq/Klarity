cmake_minimum_required(VERSION 3.24)
project(jnidecoder VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 23)

add_library(jni_decoder SHARED src/decoder_NativeDecoder.cpp)

target_include_directories(jni_decoder PRIVATE include)

# JNI

find_package(JNI)

if (JNI_FOUND)
    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message(STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
    target_include_directories(jni_decoder PRIVATE ${JNI_INCLUDE_DIRS})
else ()
    message(FATAL_ERROR "JNI not found.")
endif ()

# FFMPEG

SET(FFMPEG_INCLUDE_PATH "include/ffmpeg")
target_include_directories(jni_decoder PRIVATE ${FFMPEG_INCLUDE_PATH})

SET(FFMPEG_BIN_PATH "bin/ffmpeg")
target_link_directories(jni_decoder PRIVATE ${FFMPEG_BIN_PATH})

target_link_libraries(jni_decoder PRIVATE
        avutil-57
        swscale-6
        swresample-4
        avcodec-59
        avformat-59
)

# KLARITY_DECODER

SET(KLARITY_DECODER_INCLUDE_PATH "include/klarity_decoder")
target_include_directories(jni_decoder PRIVATE ${KLARITY_DECODER_INCLUDE_PATH})

SET(KLARITY_DECODER_BIN_PATH "bin/klarity_decoder")
target_link_directories(jni_decoder PRIVATE ${KLARITY_DECODER_BIN_PATH})

target_link_libraries(jni_decoder PRIVATE libklarity_decoder)