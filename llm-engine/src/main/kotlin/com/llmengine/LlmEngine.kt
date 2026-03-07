package com.llmengine

import java.io.File

/**
 * Parameters used when loading a GGUF model via llama.cpp.
 *
 * @param maxTokens  Maximum tokens the model may generate per call.
 * @param temperature Sampling temperature (0 = deterministic, 1 = default).
 * @param useGpu     Reserved for future GPU/NNAPI acceleration; currently unused.
 */
data class EngineParams(
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val useGpu: Boolean = true
)

/**
 * Entry point for on-device LLM inference via llama.cpp (GGUF format).
 *
 * Usage:
 * ```kotlin
 * // Load model (blocking — call from Dispatchers.IO)
 * val session = LlmEngine.load(context, modelFile)
 *
 * // Stream response token-by-token
 * session.generate("Hello!").collect { token -> print(token) }
 *
 * // Release RAM when done
 * session.unload()
 * ```
 */
object LlmEngine {

    /**
     * Load a GGUF model file and return a ready-to-use [LlmSession].
     *
     * **This is a blocking call** — always invoke from a background coroutine:
     * ```kotlin
     * val session = withContext(Dispatchers.IO) { LlmEngine.load(modelFile) }
     * ```
     *
     * @param modelFile Absolute path to the `.gguf` model file. Use [ModelManager] to download.
     * @param params    Optional engine configuration (token limit, temperature).
     * @throws IllegalArgumentException if [modelFile] does not exist.
     * @throws RuntimeException         if llama.cpp fails to load the model.
     */
    fun load(
        modelFile: File,
        params: EngineParams = EngineParams()
    ): LlmSession {
        require(modelFile.exists()) { "Model file not found: ${modelFile.absolutePath}" }
        require(modelFile.length() > 0) { "Model file is empty: ${modelFile.absolutePath}" }
        return LlamaCppSession(modelFile, params)
    }
}
