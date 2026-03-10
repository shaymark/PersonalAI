package com.personal.personalai.data.datasource.ai

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.llmengine.EngineParams
import com.llmengine.GenerationParams
import com.llmengine.LlmEngine
import com.llmengine.LlmSession
import com.llmengine.ModelManager
import com.llmengine.Models
import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.presentation.settings.PreferencesKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalLlmDataSource"

/** Shared [GenerationParams] used for every local inference call. */
private val GENERATION_PARAMS = GenerationParams(
    maxTokens   = 1024,
    temperature = 0.7f,
    topP        = 0.9f,
    stopStrings = listOf("<|im_end|>", "<|endoftext|>")
)

/**
 * On-device LLM inference backend using the [LlmEngine] / llama.cpp library.
 *
 * A single [LlmSession] is kept alive and reused across calls. The session is
 * reloaded automatically when the user selects a different model. No internet
 * access is required once a model file has been downloaded.
 *
 * Prompt format: ChatML — compatible with Qwen3.5 and other instruction-tuned GGUF models.
 * Tool results are text-only (function/tool calling is not supported in local mode).
 * The tag parser ([TASK:{...}], [MEMORY:{...}], etc.) is still active so scheduling
 * and memory features work when the local model follows the system prompt instructions.
 */
@Singleton
class LocalLlmDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val modelManager = ModelManager(context)

    @Volatile private var currentSession: LlmSession? = null
    @Volatile private var currentModelId: String? = null

    // ── Session management ────────────────────────────────────────────────────

    /**
     * Returns the active [LlmSession] for the currently selected model,
     * loading it from disk if needed. Returns `null` if no model is selected
     * or the model file is not present on disk.
     */
    private suspend fun getSession(): LlmSession? {
        val modelId = dataStore.data.first()[PreferencesKeys.LOCAL_MODEL_ID].orEmpty()
        if (modelId.isBlank()) {
            Log.w(TAG, "No local model selected (LOCAL_MODEL_ID is blank)")
            return null
        }

        // Reuse the existing session if the model hasn't changed
        val existing = currentSession
        if (modelId == currentModelId && existing?.isLoaded == true) {
            Log.d(TAG, "Reusing loaded session for model: $modelId")
            return existing
        }

        Log.d(TAG, "Loading model: $modelId")

        // Unload the previous session before loading a new one
        existing?.unload()
        currentSession = null
        currentModelId = null

        val descriptor = Models.all.find { it.id == modelId }
        if (descriptor == null) {
            Log.e(TAG, "Unknown model ID: $modelId")
            return null
        }

        val file = modelManager.getModelFile(descriptor)
        if (file == null) {
            Log.e(TAG, "Model file not found on disk for: $modelId")
            return null
        }

        Log.d(TAG, "Model file found: ${file.absolutePath} (${file.length() / 1_048_576} MB)")

        return withContext(Dispatchers.IO) {
            LlmEngine.load(
                modelFile = file,
                // useGpu = false: Qwen3.5 4B is a hybrid SSM+Transformer model.
                // The Gated Delta Net (SSM) layers are unsupported on Vulkan — they fall back
                // to CPU, creating 23 GPU↔CPU graph splits per token. This overwhelms the
                // Adreno 750 Vulkan driver after ~50 s of sustained generation (kgsl-timeline
                // fences stop signalling → "Failed to link shaders" → SIGSEGV in ggml_vk_mul_mat).
                // CPU-only via ARM NEON is stable and gives comparable throughput since the GPU
                // was only accelerating 8/32 transformer layers anyway.
                params    = EngineParams(maxTokens = 1024, temperature = 0.7f, useGpu = false)
            ).also {
                currentSession  = it
                currentModelId  = modelId
                Log.d(TAG, "Model loaded successfully: $modelId")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Call [generateBlocking] and guard against a blank result. An empty response
     * indicates the model emitted an EOG token immediately (wrong prompt format or
     * hardware issue) — we surface this as an exception so the caller's [runCatching]
     * converts it to [Result.failure] and shows the user an error message.
     */
    private suspend fun generateOrThrow(session: LlmSession, prompt: String): String {
        Log.d(TAG, "Starting generation, prompt length: ${prompt.length} chars")
        val rawText = session.generateBlocking(prompt = prompt, params = GENERATION_PARAMS).trim()
        Log.d(TAG, "Generation complete, response length: ${rawText.length} chars")

        if (rawText.isBlank()) {
            error(
                "The model returned an empty response.\n" +
                "This can happen if:\n" +
                "• The model generated only an end-of-sequence token\n" +
                "• The device ran out of RAM during inference\n" +
                "• The GGUF file is corrupted — try re-downloading the model"
            )
        }
        return rawText
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generate a response to [message] using the local GGUF model.
     * Includes [chatHistory] (last 6 turns) and [memories] in the prompt.
     */
    suspend fun sendMessage(
        message: String,
        chatHistory: List<Message>,
        memories: List<Memory>
    ): Result<String> = runCatching {
        val session = getSession()
            ?: error(
                "No local model is loaded. Please download and select a model in " +
                "Settings → AI Backend → Local LLM."
            )
        val prompt = PromptTemplates.buildLocalPrompt(message, chatHistory, memories)
        generateOrThrow(session, prompt)
    }

    /**
     * Agent-loop variant. Extracts the last user message from [conversationItems]
     * (OpenAI Responses API format) and generates a text response. Tool calls are
     * not supported in local mode — the model returns [AgentResponse.Text] only.
     */
    suspend fun sendMessageWithTools(
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse> = runCatching {
        val session = getSession()
            ?: error(
                "No local model is loaded. Please download and select a model in " +
                "Settings → AI Backend → Local LLM."
            )

        // Extract the last user-role message text from the conversation array
        val lastUserText = (0 until conversationItems.length())
            .map { conversationItems.getJSONObject(it) }
            .lastOrNull { it.optString("role") == "user" }
            ?.optString("content")
            .orEmpty()

        if (lastUserText.isBlank()) {
            Log.w(TAG, "No user message found in conversationItems (length=${conversationItems.length()})")
        } else {
            Log.d(TAG, "Extracted user text (${lastUserText.length} chars): ${lastUserText.take(80)}")
        }

        val prompt = PromptTemplates.buildLocalPrompt(lastUserText, emptyList(), memories)
        AgentResponse.Text(generateOrThrow(session, prompt))
    }

    /**
     * Explicitly releases native model resources. Call when the user switches to a
     * different provider to free RAM immediately rather than waiting for GC.
     */
    fun unloadSession() {
        Log.d(TAG, "Unloading session")
        currentSession?.unload()
        currentSession = null
        currentModelId = null
    }
}
