cmake_minimum_required(VERSION 3.17.5)
project(klarity VERSION 1.1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(FFMPEG_INCLUDE "" CACHE PATH "Path to FFmpeg include dir")
set(PORTAUDIO_INCLUDE "" CACHE PATH "Path to PortAudio include dir")
set(FFMPEG_BIN "" CACHE PATH "Path to FFmpeg binary dir")
set(PORTAUDIO_BIN "" CACHE PATH "Path to PortAudio binary dir")

find_package(JNI REQUIRED)

add_library(klarity SHARED
        src/common.cpp
        src/decoder/decoder.cpp
        src/decoder/hwaccel.cpp
        src/sampler/sampler.cpp
        src/data/com_github_numq_klarity_core_data_NativeData.cpp
        src/decoder/com_github_numq_klarity_core_decoder_NativeDecoder.cpp
        src/sampler/com_github_numq_klarity_core_sampler_NativeSampler.cpp
)

target_include_directories(klarity PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${FFMPEG_INCLUDE}
        ${PORTAUDIO_INCLUDE}
        include
        include/data
        include/decoder
        include/decoder/ffmpeg
        include/sampler
        include/sampler/dsp
        include/sampler/portaudio
        include/sampler/stretch
)

target_link_directories(klarity PRIVATE
        ${FFMPEG_BIN}
        ${PORTAUDIO_BIN}
)

target_link_libraries(klarity PRIVATE
        avcodec
        avformat
        avutil
        swresample
        swscale
        portaudio
)

message(STATUS "FFMPEG_INCLUDE: ${FFMPEG_INCLUDE}")
message(STATUS "PORTAUDIO_INCLUDE: ${PORTAUDIO_INCLUDE}")
message(STATUS "FFMPEG_BIN: ${FFMPEG_BIN}")
message(STATUS "PORTAUDIO_BIN: ${PORTAUDIO_BIN}")