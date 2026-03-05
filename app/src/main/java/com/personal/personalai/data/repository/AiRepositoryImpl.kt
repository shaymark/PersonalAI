package com.personal.personalai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.data.datasource.ai.LocalLlmDataSource
import com.personal.personalai.data.datasource.ai.MockAiDataSource
import com.personal.personalai.data.datasource.ai.OpenAiDataSource
import com.personal.personalai.data.datasource.ai.WhisperDataSource
import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.MemoryRepository
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
 * - Provider == "local_llm"         → [LocalLlmDataSource] (on-device GGUF inference)
 * - Provider == "openai" + API key  → [OpenAiDataSource]  (GPT-4o via Responses API)
 * - Provider == "openai" + no key   → [MockAiDataSource]  (deterministic offline fallback)
 *
 * Loads the user's memories before each call and passes them to the data source so they
 * can be injected into the system prompt.
 *
 * This class owns no AI logic itself; it is purely a router.
 */
class AiRepositoryImpl @Inject constructor(
    private val openAiDataSource: OpenAiDataSource,
    private val mockAiDataSource: MockAiDataSource,
    private val localLlmDataSource: LocalLlmDataSource,
    private val whisperDataSource: WhisperDataSource,
    private val dataStore: DataStore<Preferences>,
    private val memoryRepository: MemoryRepository
) : AiRepository {

    private suspend fun isLocalLlm(): Boolean =
        dataStore.data.first()[PreferencesKeys.AI_PROVIDER] == "local_llm"

    override suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        val memories = memoryRepository.getMemories().first()

        if (isLocalLlm()) {
            return localLlmDataSource.sendMessage(message, chatHistory, memories)
        }

        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            mockAiDataSource.sendMessage(message, chatHistory, memories)
        } else {
            // chatHistory from SendMessageUseCase already contains the user's message as the last
            // entry. For callers like TaskReminderWorker that pass emptyList(), we append the
            // message here so OpenAI always receives at least one user turn.
            val inputHistory = if (chatHistory.lastOrNull()?.content == message) {
                chatHistory
            } else {
                chatHistory + Message(content = message, role = MessageRole.USER)
            }
            openAiDataSource.sendMessage(apiKey, inputHistory, memories)
        }
    }

    override suspend fun sendMessageWithTools(
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse> {
        if (isLocalLlm()) {
            return localLlmDataSource.sendMessageWithTools(conversationItems, memories, tools)
        }

        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            Result.success(
                AgentResponse.Text(
                    "I need an OpenAI API key or a local model to respond. " +
                    "Please configure one in Settings → AI Backend."
                )
            )
        } else {
            openAiDataSource.sendMessageWithTools(apiKey, conversationItems, memories, tools)
        }
    }

    override suspend fun transcribeAudio(audioFile: File): Result<String> {
        // Audio transcription always requires the Whisper API — not available with local models
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            Result.failure(Exception("Whisper requires an OpenAI API key. Add one in Settings."))
        } else {
            whisperDataSource.transcribe(apiKey, audioFile)
        }
    }
}
