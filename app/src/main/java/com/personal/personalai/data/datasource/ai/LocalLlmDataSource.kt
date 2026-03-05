package com.personal.personalai.data.datasource.ai

import android.content.Context
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
        if (modelId.isBlank()) return null

        // Reuse the existing session if the model hasn't changed
        val existing = currentSession
        if (modelId == currentModelId && existing?.isLoaded == true) return existing

        // Unload the previous session before loading a new one
        existing?.unload()
        currentSession = null
        currentModelId = null

        val descriptor = Models.all.find { it.id == modelId } ?: return null
        val file = modelManager.getModelFile(descriptor) ?: return null

        return withContext(Dispatchers.IO) {
            LlmEngine.load(
                modelFile = file,
                params = EngineParams(contextSize = 2048, threads = 4, gpuLayers = 0)
            ).also {
                currentSession = it
                currentModelId = modelId
            }
        }
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
                "No local model is loaded. Please download and select a model in Settings → " +
                "AI Backend → Local LLM."
            )
        val prompt = PromptTemplates.buildLocalPrompt(message, chatHistory, memories)
        session.generateBlocking(
            prompt = prompt,
            params = GenerationParams(
                maxTokens = 1024,
                temperature = 0.7f,
                topP = 0.9f,
                stopStrings = listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")
            )
        ).trim()
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
                "No local model is loaded. Please download and select a model in Settings → " +
                "AI Backend → Local LLM."
            )

        // Extract the last user-role message text from the conversation array
        val lastUserText = (0 until conversationItems.length())
            .map { conversationItems.getJSONObject(it) }
            .lastOrNull { it.optString("role") == "user" }
            ?.optString("content")
            .orEmpty()

        val prompt = PromptTemplates.buildLocalPrompt(lastUserText, emptyList(), memories)
        AgentResponse.Text(
            session.generateBlocking(
                prompt = prompt,
                params = GenerationParams(
                    maxTokens = 1024,
                    temperature = 0.7f,
                    topP = 0.9f,
                    stopStrings = listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")
                )
            ).trim()
        )
    }

    /**
     * Explicitly releases native model resources. Call when the user switches to a
     * different provider to free RAM immediately rather than waiting for GC.
     */
    fun unloadSession() {
        currentSession?.unload()
        currentSession = null
        currentModelId = null
    }
}
