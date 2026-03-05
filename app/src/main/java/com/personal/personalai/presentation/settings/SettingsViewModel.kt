package com.personal.personalai.presentation.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmengine.DownloadState
import com.llmengine.ModelDescriptor
import com.llmengine.ModelManager
import com.personal.personalai.data.datasource.ai.LocalLlmDataSource
import com.personal.personalai.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

object PreferencesKeys {
    val API_KEY        = stringPreferencesKey("api_key")
    val AI_PROVIDER    = stringPreferencesKey("ai_provider")
    val LOCAL_MODEL_ID = stringPreferencesKey("local_model_id")
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val chatRepository: ChatRepository,
    private val localLlmDataSource: LocalLlmDataSource
) : ViewModel() {

    private val modelManager = ModelManager(context)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.collect { preferences ->
                val providerStr  = preferences[PreferencesKeys.AI_PROVIDER] ?: "openai"
                val provider     = if (providerStr == "local_llm") AiProvider.LOCAL_LLM else AiProvider.OPENAI
                val selectedId   = preferences[PreferencesKeys.LOCAL_MODEL_ID] ?: ""
                val downloadedIds = modelManager.listDownloaded().map { it.id }.toSet()

                _uiState.update {
                    it.copy(
                        apiKey            = preferences[PreferencesKeys.API_KEY] ?: "",
                        aiProvider        = provider,
                        selectedModelId   = selectedId,
                        downloadedModelIds = downloadedIds
                    )
                }
            }
        }
    }

    // ── OpenAI settings ───────────────────────────────────────────────────────

    fun onApiKeyChanged(key: String) =
        _uiState.update { it.copy(apiKey = key, savedSuccessfully = false) }

    fun saveApiKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                dataStore.edit { prefs -> prefs[PreferencesKeys.API_KEY] = _uiState.value.apiKey }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ── Provider selection ────────────────────────────────────────────────────

    fun setAiProvider(provider: AiProvider) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.AI_PROVIDER] = if (provider == AiProvider.LOCAL_LLM) "local_llm" else "openai"
            }
            // Free RAM immediately when the user switches away from local mode
            if (provider == AiProvider.OPENAI) localLlmDataSource.unloadSession()
            _uiState.update { it.copy(aiProvider = provider) }
        }
    }

    // ── Local model management ────────────────────────────────────────────────

    /** Persist the selected model ID so [LocalLlmDataSource] picks it up on next call. */
    fun selectModel(model: ModelDescriptor) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.LOCAL_MODEL_ID] = model.id }
            _uiState.update { it.copy(selectedModelId = model.id) }
        }
    }

    /**
     * Download [model] from HuggingFace, streaming progress into the UI state.
     * Auto-selects the model on completion.
     */
    fun downloadModel(model: ModelDescriptor) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingModelId = model.id, downloadError = null) }
            modelManager.download(model).collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        _uiState.update {
                            it.copy(
                                downloadProgress = it.downloadProgress + (model.id to state.fraction)
                            )
                        }
                    }
                    is DownloadState.Done -> {
                        val downloaded = modelManager.listDownloaded().map { it.id }.toSet()
                        dataStore.edit { prefs -> prefs[PreferencesKeys.LOCAL_MODEL_ID] = model.id }
                        _uiState.update {
                            it.copy(
                                downloadingModelId = null,
                                downloadedModelIds  = downloaded,
                                selectedModelId     = model.id,
                                downloadProgress    = it.downloadProgress - model.id
                            )
                        }
                    }
                    is DownloadState.Failed -> {
                        _uiState.update {
                            it.copy(
                                downloadingModelId = null,
                                downloadError      = state.error.message ?: "Download failed",
                                downloadProgress   = it.downloadProgress - model.id
                            )
                        }
                    }
                }
            }
        }
    }

    /** Delete [model]'s file from disk and update the UI state accordingly. */
    fun deleteModel(model: ModelDescriptor) {
        viewModelScope.launch {
            modelManager.delete(model)
            val downloaded = modelManager.listDownloaded().map { it.id }.toSet()
            val selectedId = _uiState.value.selectedModelId
            _uiState.update {
                it.copy(
                    downloadedModelIds = downloaded,
                    selectedModelId    = if (selectedId == model.id) "" else selectedId
                )
            }
            // Clear the persisted selection if it pointed to the deleted model
            if (selectedId == model.id) {
                dataStore.edit { prefs -> prefs[PreferencesKeys.LOCAL_MODEL_ID] = "" }
                localLlmDataSource.unloadSession()
            }
        }
    }

    fun dismissDownloadError() = _uiState.update { it.copy(downloadError = null) }

    // ── Chat history ──────────────────────────────────────────────────────────

    fun showClearHistoryDialog()    = _uiState.update { it.copy(showClearHistoryDialog = true) }
    fun dismissClearHistoryDialog() = _uiState.update { it.copy(showClearHistoryDialog = false) }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
            _uiState.update { it.copy(showClearHistoryDialog = false) }
        }
    }
}
