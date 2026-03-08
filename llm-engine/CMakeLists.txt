cmake_minimum_required(VERSION 3.22.1)
project("llama-engine")

include(FetchContent)

# ── Fetch Vulkan-Headers ─────────────────────────────────────────────────────
# NDK includes vulkan/vulkan.h (C API) but NOT vulkan/vulkan.hpp (C++ bindings).
# llama.cpp's Vulkan backend uses vulkan.hpp — fetch it from Khronos.
FetchContent_Declare(
    VulkanHeaders
    URL https://github.com/KhronosGroup/Vulkan-Headers/archive/refs/tags/v1.3.290.zip
)
FetchContent_MakeAvailable(VulkanHeaders)

# ── Satisfy llama.cpp's find_package(Vulkan COMPONENTS glslc REQUIRED) ──────
# llama.cpp reads Vulkan_GLSLC_EXECUTABLE to pass to vulkan-shaders-gen.
# NDK ships glslc at a known path inside shader-tools/.
if(ANDROID AND NOT TARGET Vulkan::Vulkan)

    # Find the NDK's glslc (host binary — runs on macOS/Linux, not on Android)
    find_program(_glslc glslc
        HINTS
            "${ANDROID_NDK}/shader-tools/darwin-x86_64"
            "${ANDROID_NDK}/shader-tools/linux-x86_64"
            "${CMAKE_ANDROID_NDK}/shader-tools/darwin-x86_64"
            "${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64"
        NO_DEFAULT_PATH
    )
    if(NOT _glslc)
        find_program(_glslc glslc)   # fallback: system PATH (e.g. standalone Vulkan SDK)
    endif()
    if(NOT _glslc)
        message(FATAL_ERROR
            "glslc not found in NDK shader-tools.\n"
            "ANDROID_NDK=${ANDROID_NDK}\n"
            "Install NDK 27+ or add glslc to your PATH.")
    endif()
    message(STATUS "Vulkan glslc: ${_glslc}")

    # Set the variable llama.cpp actually reads (was the missing piece in previous builds)
    set(Vulkan_GLSLC_EXECUTABLE "${_glslc}" CACHE FILEPATH "glslc shader compiler" FORCE)
    set(Vulkan_glslc_FOUND      TRUE        CACHE BOOL   ""                        FORCE)

    # Vulkan::Vulkan — headers + system libvulkan.so
    add_library(Vulkan::Vulkan INTERFACE IMPORTED GLOBAL)
    set_target_properties(Vulkan::Vulkan PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${vulkanheaders_SOURCE_DIR}/include"
        INTERFACE_LINK_LIBRARIES      "vulkan"
    )

    # Vulkan::glslc — the executable target CMake's FindVulkan creates for the glslc component
    add_executable(Vulkan::glslc IMPORTED GLOBAL)
    set_target_properties(Vulkan::glslc PROPERTIES
        IMPORTED_LOCATION "${_glslc}"
    )

    set(Vulkan_INCLUDE_DIR "${vulkanheaders_SOURCE_DIR}/include" CACHE PATH   "" FORCE)
    set(Vulkan_LIBRARY     "vulkan"                              CACHE STRING "" FORCE)
    set(Vulkan_FOUND       TRUE                                  CACHE BOOL   "" FORCE)
    set(Vulkan_VERSION     "1.3.290"                             CACHE STRING "" FORCE)

endif()

# ── Fetch llama.cpp ──────────────────────────────────────────────────────────
FetchContent_Declare(
    llama
    URL https://github.com/ggerganov/llama.cpp/archive/refs/tags/b8233.zip
)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN          ON  CACHE BOOL "Enable Vulkan GPU backend" FORCE)

# Adreno 750 (Snapdragon 8 Gen 3) has driver bugs with cooperative-matrix pipelines:
# they silently return null, then llama.cpp dereferences the null → SIGSEGV in ggml_vk_mul_mat.
# Disabling these paths removes ~10% peak speed but GPU is still fully used for inference.
set(GGML_VULKAN_COOPMAT_GLSLC_SUPPORT  OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_COOPMAT2_GLSLC_SUPPORT OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama)

# ── JNI bridge ───────────────────────────────────────────────────────────────
add_library(llama-engine SHARED
    src/main/cpp/llama-bridge.cpp
)

target_include_directories(llama-engine PRIVATE
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/ggml/include
)

# 'llama' transitively pulls in ggml, ggml-vulkan, ggml-cpu, ggml-base, etc.
# Listing them explicitly causes duplicate-library warnings — keep this minimal.
target_link_libraries(llama-engine
    llama
    android
    log
    vulkan
)

target_link_options(llama-engine PRIVATE "LINKER:--error-limit=0")
