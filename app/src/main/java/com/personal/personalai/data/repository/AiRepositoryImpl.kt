package com.personal.personalai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.presentation.settings.PreferencesKeys
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Central [AiRepository] implementation that decides at runtime which backend to use:
 * - If an OpenAI API key is stored in DataStore → delegates to [OpenAiRepositoryImpl]
 * - Otherwise → delegates to [MockAiRepositoryImpl]
 *
 * This class owns no AI logic itself; it is purely a router.
 */
class AiRepositoryImpl @Inject constructor(
    private val openAiRepository: OpenAiRepositoryImpl,
    private val mockAiRepository: MockAiRepositoryImpl,
    private val dataStore: DataStore<Preferences>
) : AiRepository {

    override suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            mockAiRepository.sendMessage(message, chatHistory)
        } else {
            openAiRepository.sendMessage(apiKey, chatHistory)
        }
    }
}
