cmake_minimum_required(VERSION 3.24)
project(klarity VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 20)

set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_SHARED_LIBRARY_PREFIX "")

add_library(klarity SHARED
        src/common.cpp
        src/decoder/decoder.cpp
        src/sampler/sampler.cpp
        src/decoder/com_github_numq_klarity_core_decoder_NativeDecoder.cpp
        src/sampler/com_github_numq_klarity_core_sampler_NativeSampler.cpp
)

find_package(JNI)

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

target_link_directories(klarity PRIVATE
        bin/ffmpeg
        bin/portaudio
)

target_link_libraries(klarity PRIVATE
        avcodec-61
        avformat-61
        avutil-59
        swresample-5
        swscale-8
        libportaudio
)

#find_package(JNI)
#
#add_library(klarity-decoder SHARED
#        src/decoder/decoder.cpp
#        src/decoder/com_github_numq_klarity_core_decoder_NativeDecoder.cpp
#)
#
#add_library(klarity-sampler SHARED
#        src/sampler/sampler.cpp
#        src/sampler/com_github_numq_klarity_core_sampler_NativeSampler.cpp
#)
#
#if (JNI_FOUND)
#    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
#    message(STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
#    target_include_directories(klarity-decoder PRIVATE ${JNI_INCLUDE_DIRS})
#    target_include_directories(klarity-sampler PRIVATE ${JNI_INCLUDE_DIRS})
#else ()
#    message(FATAL_ERROR "JNI not found.")
#endif ()
#
#target_include_directories(klarity-decoder PRIVATE
#        include/decoder
#        include/decoder/ffmpeg
#)
#
#target_include_directories(klarity-sampler PRIVATE
#        include/sampler
#        include/sampler/dsp
#        include/sampler/portaudio
#        include/sampler/stretch
#)
#
#target_link_directories(klarity-decoder PRIVATE bin/ffmpeg)
#
#target_link_directories(klarity-sampler PRIVATE bin/portaudio)
#
#target_link_libraries(klarity-decoder PRIVATE avcodec-61 avformat-61 avutil-59 swresample-5 swscale-8)
#
#target_link_libraries(klarity-sampler PRIVATE libportaudio)