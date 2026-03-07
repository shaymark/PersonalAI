cmake_minimum_required(VERSION 3.22.1)
project("llama-engine")

# ── Fetch llama.cpp ────────────────────────────────────────────────────────────
include(FetchContent)

FetchContent_Declare(
    llama
    URL https://github.com/ggerganov/llama.cpp/archive/refs/tags/b8233.zip
)

# Disable tools and examples to keep build fast
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama)

# ── JNI bridge shared library ──────────────────────────────────────────────────
add_library(llama-engine SHARED
    src/main/cpp/llama-bridge.cpp
)

target_include_directories(llama-engine PRIVATE
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/ggml/include
)

target_link_libraries(llama-engine
    llama
    android
    log
)
