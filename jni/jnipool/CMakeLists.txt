cmake_minimum_required(VERSION 3.24)
project(jnipool)

set(CMAKE_CXX_STANDARD 23)

add_library(jnipool INTERFACE)

target_include_directories(jnipool INTERFACE include)