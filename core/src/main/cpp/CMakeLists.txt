cmake_minimum_required(VERSION 3.24)
project(klarity VERSION 1.1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_SHARED_LIBRARY_PREFIX "")

set(BIN_FOLDER bin)

add_library(klarity SHARED
        src/common.cpp
        src/decoder/decoder.cpp
        src/sampler/sampler.cpp
        src/decoder/com_github_numq_klarity_core_decoder_NativeDecoder.cpp
        src/sampler/com_github_numq_klarity_core_sampler_NativeSampler.cpp
)

find_package(JNI REQUIRED)
if (JNI_FOUND)
    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message(STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
    target_include_directories(klarity PRIVATE ${JNI_INCLUDE_DIRS})
else ()
    message(FATAL_ERROR "JNI not found.")
endif ()

target_include_directories(klarity PRIVATE
        include
        include/decoder
        include/decoder/ffmpeg
        include/sampler
        include/sampler/dsp
        include/sampler/portaudio
        include/sampler/stretch
)

target_link_directories(klarity PRIVATE ${BIN_FOLDER})

target_link_libraries(klarity PRIVATE
        avcodec
        avformat
        avutil
        swresample
        swscale
        portaudio
)
