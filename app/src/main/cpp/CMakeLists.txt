cmake_minimum_required(VERSION 3.22.1)
project(s24_native LANGUAGES C)

add_library(s24_native SHARED native_probe.c)
target_compile_options(s24_native PRIVATE -O2 -Wall -Wextra -Werror)
target_link_libraries(s24_native PRIVATE log)

