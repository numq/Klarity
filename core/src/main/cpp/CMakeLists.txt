cmake_minimum_required(VERSION 3.17.5)
project(klarity VERSION 1.1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_SHARED_LIBRARY_PREFIX "")

set(BIN_FOLDER "${CMAKE_SOURCE_DIR}/bin")
set(LIB_FOLDER "${CMAKE_SOURCE_DIR}/lib")

add_library(klarity SHARED
        src/common.cpp
        src/decoder/decoder.cpp
        src/decoder/hwaccel.cpp
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
        include/memory
        include/sampler
        include/sampler/dsp
        include/sampler/portaudio
        include/sampler/stretch
)

target_link_directories(klarity PRIVATE ${BIN_FOLDER} ${LIB_FOLDER})

target_link_libraries(klarity PRIVATE
        avutil
        postproc
        swresample
        swscale
        avcodec
        avformat
        avfilter
        avdevice
        portaudio
)