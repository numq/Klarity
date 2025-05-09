cmake_minimum_required(VERSION 3.17.5)
project(klarity VERSION 1.1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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

if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    find_package(FFMPEG REQUIRED COMPONENTS avcodec avformat avutil swscale swresample)
    find_package(PORTAUDIO REQUIRED)

    target_compile_definitions(klarity PRIVATE DEBUG_MODE)
    target_compile_options(klarity PRIVATE -g)

    target_include_directories(klarity PRIVATE
            ${JNI_INCLUDE_DIRS}
            ${FFMPEG_INCLUDE_DIRS}
            ${PORTAUDIO_INCLUDE_DIRS}
            include
            include/data
            include/decoder
            include/decoder/ffmpeg
            include/sampler
            include/sampler/dsp
            include/sampler/portaudio
            include/sampler/stretch
    )

    target_link_libraries(klarity PRIVATE
            ${FFMPEG_LIBRARIES}
            portaudio
    )
elseif (CMAKE_BUILD_TYPE STREQUAL "Release")
    set(FFMPEG_INCLUDE "" CACHE PATH "Path to FFmpeg include dir")
    set(PORTAUDIO_INCLUDE "" CACHE PATH "Path to PortAudio include dir")
    set(FFMPEG_BIN "" CACHE PATH "Path to FFmpeg binary dir")
    set(PORTAUDIO_BIN "" CACHE PATH "Path to PortAudio binary dir")

    target_compile_definitions(klarity PRIVATE NDEBUG)
    target_compile_options(klarity PRIVATE -O3)

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
endif ()