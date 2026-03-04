package com.personal.personalai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * - If an OpenAI API key is stored in DataStore → delegates to [OpenAiDataSource]
 * - Otherwise → delegates to [MockAiDataSource]
 *
 * Loads the user's memories before each call and passes them to the data source so they
 * can be injected into the system prompt.
 *
 * This class owns no AI logic itself; it is purely a router.
 */
class AiRepositoryImpl @Inject constructor(
    private val openAiDataSource: OpenAiDataSource,
    private val mockAiDataSource: MockAiDataSource,
    private val whisperDataSource: WhisperDataSource,
    private val dataStore: DataStore<Preferences>,
    private val memoryRepository: MemoryRepository
) : AiRepository {

    override suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        val memories = memoryRepository.getMemories().first()
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
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            Result.success(AgentResponse.Text("I need an OpenAI API key to use tools. Please add one in Settings."))
        } else {
            openAiDataSource.sendMessageWithTools(apiKey, conversationItems, memories, tools)
        }
    }

    override suspend fun transcribeAudio(audioFile: File): Result<String> {
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            Result.failure(Exception("Whisper requires an OpenAI API key. Add one in Settings."))
        } else {
            whisperDataSource.transcribe(apiKey, audioFile)
        }
    }
}
