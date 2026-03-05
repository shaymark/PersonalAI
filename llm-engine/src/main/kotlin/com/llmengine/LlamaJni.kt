package com.llmengine

/**
 * Low-level JNI bridge to the native llama.cpp library.
 *
 * **Do not use this directly** — use [LlmEngine] and [LlmSession] instead.
 * All functions declared here map 1-to-1 to functions in `llama_jni.cpp`.
 */
internal object LlamaJni {

    init {
        System.loadLibrary("llmengine")
    }

    /**
     * Load a GGUF model file from disk and create a llama context.
     *
     * @param path        Absolute path to the `.gguf` file.
     * @param ctxSize     KV-cache context length (number of tokens).
     * @param nThreads    CPU threads used for inference.
     * @param nGpuLayers  Number of transformer layers to offload to GPU (0 = CPU only).
     * @return Opaque native handle (> 0) on success, 0 on failure.
     */
    external fun nativeLoadModel(
        path: String,
        ctxSize: Int,
        nThreads: Int,
        nGpuLayers: Int
    ): Long

    /**
     * Generate tokens for [prompt], invoking [callback] for every text piece produced.
     *
     * This function blocks the calling thread until generation completes or [maxTokens]
     * is reached.  Call it from a background thread (e.g. [kotlinx.coroutines.Dispatchers.IO]).
     *
     * @return Number of tokens generated, or -1 on error.
     */
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        seed: Int,
        callback: TokenCallback
    ): Int

    /**
     * Release the native llama model and context held by [handle].
     * After this call the handle is invalid and must not be used.
     */
    external fun nativeFree(handle: Long)

    /** Callback interface invoked from C++ for every generated token piece. */
    interface TokenCallback {
        fun onToken(piece: String)
    }
}
