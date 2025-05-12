cmake_minimum_required(VERSION 3.17.5)
project(klarity VERSION 1.1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)
#find_package(FFMPEG REQUIRED)
#find_package(portaudio REQUIRED)

set(FFMPEG_INCLUDE_DIRS "${FFMPEG_INCLUDE_DIRS}" CACHE PATH "FFmpeg include directory")
set(FFMPEG_LIBRARY_DIRS "${FFMPEG_LIBRARY_DIRS}" CACHE PATH "FFmpeg library directory")
set(FFMPEG_LIBRARIES "${FFMPEG_LIBRARIES}" CACHE STRING "FFmpeg libraries")
set(PORTAUDIO_INCLUDE_DIRS "${PORTAUDIO_INCLUDE_DIRS}" CACHE PATH "PortAudio include directory")
set(PORTAUDIO_LIBRARY_DIRS "${PORTAUDIO_LIBRARY_DIRS}" CACHE PATH "PortAudio library directory")

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

target_link_directories(klarity PRIVATE
        ${FFMPEG_LIBRARY_DIRS}
        ${PORTAUDIO_LIBRARY_DIRS}
)

target_link_libraries(klarity PRIVATE
        ${FFMPEG_LIBRARIES}
        portaudio
)