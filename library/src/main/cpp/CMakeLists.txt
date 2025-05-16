#cmake_minimum_required(VERSION 3.17.5)
#project(klarity VERSION 1.1.0)
#
#set(CMAKE_CXX_STANDARD 20)
#set(CMAKE_CXX_STANDARD_REQUIRED ON)
#
#set(FFMPEG_INCLUDE_DIRS "")
#set(PORTAUDIO_INCLUDE_DIRS "")
#
#set(FFMPEG_LIBRARY_DIRS "")
#set(PORTAUDIO_LIBRARY_DIRS "")
#
#find_package(JNI REQUIRED)
#
#add_library(klarity SHARED
#        src/common.cpp
#        src/decoder/decoder.cpp
#        src/decoder/hwaccel.cpp
#        src/sampler/sampler.cpp
#        src/data/com_github_numq_klarity_core_data_NativeData.cpp
#        src/decoder/com_github_numq_klarity_core_decoder_NativeDecoder.cpp
#        src/sampler/com_github_numq_klarity_core_sampler_NativeSampler.cpp
#)
#
#target_include_directories(klarity PRIVATE
#        ${JNI_INCLUDE_DIRS}
#        ${FFMPEG_INCLUDE_DIRS}
#        ${PORTAUDIO_INCLUDE_DIRS}
#        include
#        include/data
#        include/decoder
#        include/decoder/ffmpeg
#        include/sampler
#        include/sampler/dsp
#        include/sampler/portaudio
#        include/sampler/stretch
#)
#
#target_link_directories(klarity PRIVATE
#        ${FFMPEG_LIBRARY_DIRS}
#        ${PORTAUDIO_LIBRARY_DIRS}
#)
#
#target_link_libraries(klarity PRIVATE
#        avcodec
#        avformat
#        avutil
#        swresample
#        swscale
#        portaudio
#)

cmake_minimum_required(VERSION 3.17.5)
project(klarity VERSION 1.1.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)
find_package(FFMPEG REQUIRED)
find_package(portaudio REQUIRED)

add_library(klarity SHARED
        src/common.cpp
        src/decoder/decoder.cpp
        src/decoder/hwaccel.cpp
        src/sampler/sampler.cpp
        src/decoder/io_github_numq_klarity_decoder_NativeDecoder.cpp
        src/sampler/io_github_numq_klarity_sampler_NativeSampler.cpp
)

target_include_directories(klarity PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${FFMPEG_INCLUDE_DIRS}
        ${PORTAUDIO_INCLUDE_DIRS}
        include
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

target_link_options(klarity PRIVATE "/FORCE:MULTIPLE")

target_link_libraries(klarity PRIVATE
        ${FFMPEG_LIBRARIES}
        portaudio
)