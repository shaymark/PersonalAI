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
    // Stop strings for ChatML format (Qwen 2.5 / Phi 3.5 / Llama 3.2).
    // Special tokens are filtered in JNI (special=false in llama_token_to_piece),
    // but we keep these as a text-level safety net for edge cases.
    stopStrings = listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")
)

/**
 * On-device LLM inference backend using the [LlmEngine] / llama.cpp library.
 *
 * A single [LlmSession] is kept alive and reused across calls. The session is
 * reloaded automatically when the user selects a different model. No internet
 * access is required once a model file has been downloaded.
 *
 * Prompt format: ChatML — compatible with Qwen 2.5, Llama 3.2, and Phi 3.5 GGUF files.
 * Tool results are text-only (function/tool calling is not supported in this mode).
 * The [com.personal.personalai.domain.usecase.SendMessageUseCase] tag parser
 * ([TASK:{...}], [MEMORY:{...}], etc.) is still active, so scheduling and memory
 * features work when the local model follows the tag instructions in the system prompt.
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
            // gpuLayers=999 offloads ALL transformer layers to the Vulkan GPU (10–30× faster).
            // contextSize=2048 reduces KV-cache size vs 4096, cutting per-token memory bandwidth.
            // threads capped at 8 to avoid thermal throttle on high-core-count SoCs.
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
            LlmEngine.load(
                modelFile = file,
                params = EngineParams(contextSize = 2048, threads = threads, gpuLayers = 999)
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
                "• The model generated only an end-of-sequence token (try Qwen 2.5 1.5B)\n" +
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
//        val prompt = "you are helpful assistant every response need to be max 10 words, please answer this question: what is the capital of france?"
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
