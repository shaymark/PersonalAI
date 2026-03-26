package com.personal.personalai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.data.datasource.ai.OllamaDataSource
import com.personal.personalai.data.datasource.ai.OpenAiDataSource
import com.personal.personalai.data.datasource.ai.WhisperDataSource
import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.presentation.settings.PreferencesKeys
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.io.File
import javax.inject.Inject

/**
 * Central [AiRepository] implementation that decides at runtime which backend to use:
 *
 * - Provider == "ollama" + url+model → [OllamaDataSource]  (LAN Ollama server via Responses API)
 * - Provider == "openai" + API key  → [OpenAiDataSource]   (GPT-4o via OpenAI Responses API)
 * - Provider == "openai" + no key   → error message prompt to configure a key
 *
 * Loads the user's memories before each call and passes them to the data source so they
 * can be injected into the system prompt.
 *
 * This class owns no AI logic itself; it is purely a router.
 */
class AiRepositoryImpl @Inject constructor(
    private val openAiDataSource: OpenAiDataSource,
    private val ollamaDataSource: OllamaDataSource,
    private val whisperDataSource: WhisperDataSource,
    private val dataStore: DataStore<Preferences>,
) : AiRepository {

    private suspend fun readProvider(): String =
        dataStore.data.first()[PreferencesKeys.AI_PROVIDER] ?: "openai"

    override suspend fun sendMessageWithTools(
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse> {
        return when (readProvider()) {
            "ollama" -> {
                val prefs = dataStore.data.first()
                val url = prefs[PreferencesKeys.OLLAMA_URL].orEmpty().trim()
                val model = prefs[PreferencesKeys.OLLAMA_MODEL].orEmpty().trim()
                when {
                    url.isBlank() -> Result.success(
                        AgentResponse.Text(
                            "Ollama URL not configured. " +
                                    "Please enter the server URL in Settings → Dev (Ollama)."
                        )
                    )

                    model.isBlank() -> Result.success(
                        AgentResponse.Text(
                            "Ollama model not configured. " +
                                    "Please enter a model tag (e.g. qwen3.5:4b) in Settings → Dev (Ollama)."
                        )
                    )

                    else -> ollamaDataSource.sendMessageWithTools(
                        url, model, conversationItems, memories, tools
                    )
                }
            }

            else -> {
                // OpenAI path — requires an API key
                val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
                if (apiKey.isBlank()) {
                    Result.success(
                        AgentResponse.Text(
                            "I need an OpenAI API key to respond. " +
                                    "Please configure one in Settings → AI Backend."
                        )
                    )
                } else {
                    openAiDataSource.sendMessageWithTools(
                        apiKey,
                        conversationItems,
                        memories,
                        tools
                    )
                }
            }
        }
    }

    override suspend fun transcribeAudio(audioFile: File): Result<String> {
        // Audio transcription always requires the Whisper API
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            Result.failure(Exception("Whisper requires an OpenAI API key. Add one in Settings."))
        } else {
            whisperDataSource.transcribe(apiKey, audioFile)
        }
    }
}
