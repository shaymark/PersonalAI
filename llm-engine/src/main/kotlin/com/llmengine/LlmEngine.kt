package com.llmengine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/**
 * Parameters used when loading a model via MediaPipe LLM Inference.
 *
 * @param maxTokens  Maximum tokens the model may generate per call.
 * @param temperature Sampling temperature (0 = deterministic, 1 = default).
 * @param useGpu     Request GPU acceleration; MediaPipe falls back to CPU if unavailable.
 */
data class EngineParams(
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val useGpu: Boolean = true
)

/**
 * Entry point for on-device LLM inference via MediaPipe.
 *
 * Usage:
 * ```kotlin
 * // Load model (blocking — call from Dispatchers.IO)
 * val session = LlmEngine.load(context, modelFile)
 *
 * // Stream response
 * session.generate("Hello!").collect { token -> print(token) }
 *
 * // Release RAM when done
 * session.unload()
 * ```
 */
object LlmEngine {

    /**
     * Load a MediaPipe-format model file (`.task` / `.bin`) and return a ready-to-use [LlmSession].
     *
     * **This is a blocking call** — always invoke from a background coroutine:
     * ```kotlin
     * val session = withContext(Dispatchers.IO) { LlmEngine.load(context, file) }
     * ```
     *
     * @param context   Android context required by MediaPipe.
     * @param modelFile Absolute path to the model file.  Use [ModelManager] to download models.
     * @param params    Optional engine configuration (token limit, temperature, GPU).
     * @throws IllegalArgumentException if [modelFile] does not exist.
     * @throws RuntimeException         if MediaPipe fails to load the model.
     */
    fun load(
        context: Context,
        modelFile: File,
        params: EngineParams = EngineParams()
    ): LlmSession {
        require(modelFile.exists()) {
            "Model file not found: ${modelFile.absolutePath}"
        }
        require(modelFile.length() > 0) {
            "Model file is empty: ${modelFile.absolutePath}"
        }

        val backend =
            if (params.useGpu) LlmInference.Backend.GPU
            else LlmInference.Backend.CPU

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(params.maxTokens)
            .setPreferredBackend(backend)
            .build()

        val inference = LlmInference.createFromOptions(context, options)
        return MediaPipeSession(inference)
    }
}
