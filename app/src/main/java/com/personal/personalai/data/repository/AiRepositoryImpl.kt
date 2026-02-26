package com.personal.personalai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.data.datasource.ai.MockAiDataSource
import com.personal.personalai.data.datasource.ai.OpenAiDataSource
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.presentation.settings.PreferencesKeys
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Central [AiRepository] implementation that decides at runtime which backend to use:
 * - If an OpenAI API key is stored in DataStore → delegates to [OpenAiDataSource]
 * - Otherwise → delegates to [MockAiDataSource]
 *
 * This class owns no AI logic itself; it is purely a router.
 */
class AiRepositoryImpl @Inject constructor(
    private val openAiDataSource: OpenAiDataSource,
    private val mockAiDataSource: MockAiDataSource,
    private val dataStore: DataStore<Preferences>
) : AiRepository {

    override suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            mockAiDataSource.sendMessage(message, chatHistory)
        } else {
            openAiDataSource.sendMessage(apiKey, chatHistory)
        }
    }
}
