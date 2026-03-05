package com.llmengine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * An active inference session bound to a loaded model.
 *
 * Obtain an instance via [LlmEngine.load].  Each session holds native resources;
 * call [unload] when done to release them.
 */
interface LlmSession {

    /** True while native model resources are allocated. */
    val isLoaded: Boolean

    /**
     * Stream the model's response to [prompt] token-by-token.
     *
     * The returned [Flow] is cold — inference starts when the first collector subscribes
     * and runs on [Dispatchers.IO].  Collecting the flow from the Main thread is safe.
     *
     * @param prompt Full prompt string (including any chat template).
     * @param params Controls temperature, token limit, stop strings, etc.
     */
    fun generate(prompt: String, params: GenerationParams = GenerationParams()): Flow<String>

    /**
     * Convenience wrapper that collects [generate] and returns the complete response.
     * Must be called from a coroutine; suspends until generation finishes.
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
     * Release native model and context resources.
     * After calling this, [isLoaded] returns false and further calls to [generate] will throw.
     */
    fun unload()
}

// ── Internal implementation ───────────────────────────────────────────────────

internal class LlamaSession(private val handle: Long) : LlmSession {

    @Volatile
    private var _isLoaded = handle != 0L

    override val isLoaded: Boolean get() = _isLoaded

    override fun generate(prompt: String, params: GenerationParams): Flow<String> =
        callbackFlow<String> {
            check(_isLoaded) { "LlmSession has been unloaded" }

            val stopStrings = params.stopStrings
            val buffer = StringBuilder()
            var stopped = false

            LlamaJni.nativeGenerate(
                handle = handle,
                prompt = prompt,
                maxTokens = params.maxTokens,
                temperature = params.temperature,
                topP = params.topP,
                seed = params.seed,
                callback = object : LlamaJni.TokenCallback {
                    override fun onToken(piece: String) {
                        if (stopped) return
                        buffer.append(piece)
                        // Check if any stop string appears at the end of the buffer
                        if (stopStrings.isNotEmpty() && stopStrings.any { buffer.endsWith(it) }) {
                            stopped = true
                            return
                        }
                        trySend(piece)
                    }
                }
            )
            close()
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    override fun unload() {
        if (_isLoaded) {
            LlamaJni.nativeFree(handle)
            _isLoaded = false
        }
    }
}
