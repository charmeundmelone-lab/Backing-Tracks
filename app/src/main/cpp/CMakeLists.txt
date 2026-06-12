cmake_minimum_required(VERSION 3.22.1)
project(minitraxx)

find_package(oboe REQUIRED CONFIG)

add_library(minitraxx SHARED
        StemSource.cpp
        AudioEngine.cpp
        jni_bridge.cpp)

target_compile_options(minitraxx PRIVATE -Wall -O2)

target_link_libraries(minitraxx
        oboe::oboe
        android
        log)
