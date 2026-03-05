cmake_minimum_required(VERSION 3.22.1)
project(llmengine)

# ── Disable unused llama.cpp sub-targets ─────────────────────────────────────
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)   # -march=native breaks cross-compile; off is correct
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE         OFF CACHE BOOL "" FORCE)

# ── Fetch llama.cpp source (cached after first download) ─────────────────────
include(FetchContent)
FetchContent_Declare(
    llama
    URL https://github.com/ggerganov/llama.cpp/archive/refs/tags/b5620.zip
    # DOWNLOAD_EXTRACT_TIMESTAMP is a CMake 3.24+ parameter — omit for 3.22.1 compat.
)
FetchContent_MakeAvailable(llama)

# ── JNI bridge shared library ─────────────────────────────────────────────────
add_library(llmengine SHARED
    src/main/cpp/llama_jni.cpp
)

# -O3 gives meaningful throughput gains over -O2 for GGML matrix kernels
target_compile_options(llmengine PRIVATE -O3)

target_include_directories(llmengine PRIVATE
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/ggml/include
)

target_link_libraries(llmengine
    llama
    android
    log
)
