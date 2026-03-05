package com.llmengine

import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "LlmSession"

/**
 * An active inference session backed by a loaded MediaPipe model.
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
     * [Dispatchers.IO].  Collecting from the Main thread is safe.
     *
     * @param prompt Full prompt string (including any chat template tokens).
     * @param params Controls token limit, temperature, etc.
     */
    fun generate(prompt: String, params: GenerationParams = GenerationParams()): Flow<String>

    /**
     * Convenience wrapper: collects [generate] and returns the complete response.
     * Suspends until generation finishes.
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
     * Release the MediaPipe model from RAM.
     * After this call [isLoaded] is false and further calls to [generate] will throw.
     */
    fun unload()
}

// ── Internal MediaPipe implementation ─────────────────────────────────────────

internal class MediaPipeSession(private val inference: LlmInference) : LlmSession {

    @Volatile
    private var _isLoaded = true

    override val isLoaded: Boolean get() = _isLoaded

    override fun generate(prompt: String, params: GenerationParams): Flow<String> =
        callbackFlow {
            check(_isLoaded) { "LlmSession has been unloaded" }

            Log.d(TAG, "Starting generation, prompt length: ${prompt.length} chars")

            // MediaPipe calls this callback for every partial token and once more
            // with done=true when generation finishes (or errors out).
            inference.generateResponseAsync(prompt) { partialResult, done ->
                if (partialResult != null) {
                    Log.d(TAG, "onToken: $partialResult")
                    trySend(partialResult)
                }
                if (done) {
                    Log.d(TAG, "Generation complete")
                    close()
                }
            }

            // Suspend until close() is called from the callback above.
            awaitClose()
        }
            .buffer(Channel.UNLIMITED)
            .flowOn(Dispatchers.IO)

    override fun unload() {
        if (_isLoaded) {
            inference.close()
            _isLoaded = false
            Log.d(TAG, "MediaPipe inference closed")
        }
    }
}
