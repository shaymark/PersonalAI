package com.llmengine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "LlmSession"

/**
 * An active inference session backed by a loaded llama.cpp GGUF model.
 *
 * Obtain an instance via [LlmEngine.load].  Each session holds the model in RAM;
 * call [unload] when done to release resources.
 */
interface LlmSession {

    /** True while the model is loaded and ready. */
    val isLoaded: Boolean

    /**
     * Stream the model's response to [prompt] token-by-token.
     *
     * The returned [Flow] is cold — inference starts on subscription and runs on
     * a dedicated background thread.  Collecting from the Main thread is safe.
     */
    fun generate(prompt: String, params: GenerationParams = GenerationParams()): Flow<String>

    /**
     * Convenience wrapper: collects [generate] and returns the complete response.
     */
    suspend fun generateBlocking(
        prompt: String,
        params: GenerationParams = GenerationParams()
    ): String {
        val sb = StringBuilder()
        generate(prompt, params).collect { sb.append(it) }
        return sb.toString()
    }

    /**
     * Release the model from RAM.
     * After this call [isLoaded] is false and further calls to [generate] will throw.
     */
    fun unload()
}

// ── llama.cpp JNI implementation ───────────────────────────────────────────────

internal class LlamaCppSession(
    modelFile: File,
    private val params: EngineParams
) : LlmSession {

    companion object {
        init {
            System.loadLibrary("llama-engine")
        }
    }

    // JNI declarations — implemented in llama-bridge.cpp
    private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    )
    private external fun nativeFree(handle: Long)

    // Called from C++ for each decoded token
    @Suppress("unused")
    private fun onToken(token: String) {
        pendingEmit?.invoke(token)
    }

    @Volatile private var nativeHandle: Long = 0L
    @Volatile private var pendingEmit: ((String) -> Unit)? = null

    // Single background thread — llama.cpp is not thread-safe per context
    private val inferenceThread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llama-cpp-inference")
    }

    init {
        require(modelFile.exists()) { "Model file not found: ${modelFile.absolutePath}" }
        require(modelFile.length() > 0) { "Model file is empty" }

        val handle = nativeLoad(modelFile.absolutePath, nCtx = 2048, nThreads = 0)
        if (handle == 0L) error("nativeLoad returned null handle for ${modelFile.name}")
        nativeHandle = handle
        Log.d(TAG, "LlamaCppSession ready: ${modelFile.name}")
    }

    override val isLoaded: Boolean get() = nativeHandle != 0L

    override fun generate(prompt: String, params: GenerationParams): Flow<String> =
        callbackFlow {
            check(isLoaded) { "LlmSession has been unloaded" }
            Log.d(TAG, "generate(): prompt_len=${prompt.length}")

            pendingEmit = { token ->
                trySend(token)
            }

            // Run inference on the dedicated thread
            inferenceThread.submit {
                try {
                    nativeGenerate(
                        handle      = nativeHandle,
                        prompt      = prompt,
                        maxTokens   = params.maxTokens,
                        temperature = params.temperature,
                        topP        = params.topP
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "nativeGenerate error", e)
                } finally {
                    pendingEmit = null
                    close()
                }
            }

            awaitClose { pendingEmit = null }
        }
            .buffer(Channel.UNLIMITED)
            .flowOn(Dispatchers.IO)

    override fun unload() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            inferenceThread.submit { nativeFree(handle) }
            Log.d(TAG, "unload: scheduled nativeFree")
        }
    }
}
