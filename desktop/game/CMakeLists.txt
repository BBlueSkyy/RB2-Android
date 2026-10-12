# rb2 desktop build (reference for the Android port): same game code, desktop SDK.
#
# tools/desktop/build_windows.sh drives it (codegen with the desktop SDK, cross-build with clang-cl
# + xwin like Project8Recomp's M1 build).

cmake_minimum_required(VERSION 3.25)
project(rb2 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(../config/generated/rexglue.cmake)

set(RB2_REXGLUE_HOST_EXECUTABLE "" CACHE FILEPATH
    "Native rexglue executable used for codegen while cross-compiling")
if(CMAKE_CROSSCOMPILING)
    if(NOT RB2_REXGLUE_HOST_EXECUTABLE OR NOT EXISTS "${RB2_REXGLUE_HOST_EXECUTABLE}")
        message(FATAL_ERROR "Cross-compiling requires -DRB2_REXGLUE_HOST_EXECUTABLE=/path/to/native/rexglue")
    endif()
    set_target_properties(rex::rexglue PROPERTIES
        IMPORTED_LOCATION "${RB2_REXGLUE_HOST_EXECUTABLE}"
        IMPORTED_LOCATION_RELEASE "${RB2_REXGLUE_HOST_EXECUTABLE}"
        IMPORTED_LOCATION_RELWITHDEBINFO "${RB2_REXGLUE_HOST_EXECUTABLE}")
endif()

set(RB2_SOURCES main.cpp)
if(WIN32)
    list(APPEND RB2_SOURCES win_math_compat.cpp)
    add_executable(rb2 WIN32 ${RB2_SOURCES})
else()
    add_executable(rb2 ${RB2_SOURCES})
endif()

rexglue_setup_target(rb2)
target_include_directories(rb2 PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src)
