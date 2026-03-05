package com.llmengine

import java.io.File

/**
 * Parameters used when creating a llama.cpp context.
 *
 * @param contextSize KV-cache size in tokens (larger = longer context, more RAM).
 * @param threads     CPU thread count for inference (recommend ≤ physical core count).
 * @param gpuLayers   Transformer layers offloaded to GPU via Vulkan (0 = CPU only).
 */
data class EngineParams(
    val contextSize: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0
)

/**
 * Entry point for on-device LLM inference.
 *
 * Usage:
 * ```kotlin
 * // Load model (blocking — call from Dispatchers.IO)
 * val session = LlmEngine.load(modelFile)
 *
 * // Stream response
 * session.generate("Hello, who are you?").collect { token -> print(token) }
 *
 * // Release when done
 * session.unload()
 * ```
 */
object LlmEngine {

    /**
     * Load a GGUF model from [modelFile] and return a ready-to-use [LlmSession].
     *
     * **This is a blocking call** that initialises the llama.cpp backend and allocates
     * GPU/CPU buffers.  Always call it from a background coroutine:
     * ```kotlin
     * val session = withContext(Dispatchers.IO) { LlmEngine.load(file) }
     * ```
     *
     * @param modelFile Absolute path to a `.gguf` model file.  Use [ModelManager] to
     *                  download models if needed.
     * @param params    Optional engine configuration (context size, threads, GPU layers).
     * @throws IllegalArgumentException if [modelFile] does not exist.
     * @throws IllegalStateException    if llama.cpp fails to load the model or create a context.
     */
    fun load(modelFile: File, params: EngineParams = EngineParams()): LlmSession {
        require(modelFile.exists()) {
            "Model file not found: ${modelFile.absolutePath}"
        }
        require(modelFile.length() > 0) {
            "Model file is empty: ${modelFile.absolutePath}"
        }

        val handle = LlamaJni.nativeLoadModel(
            path = modelFile.absolutePath,
            ctxSize = params.contextSize,
            nThreads = params.threads,
            nGpuLayers = params.gpuLayers
        )

        check(handle != 0L) {
            "Failed to load model from '${modelFile.name}'. " +
            "Make sure the file is a valid GGUF model and the device has enough free RAM."
        }

        return LlamaSession(handle)
    }
}
