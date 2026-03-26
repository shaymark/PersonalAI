package com.personal.personalai.presentation.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

object PreferencesKeys {
    val API_KEY        = stringPreferencesKey("api_key")
    val AI_PROVIDER    = stringPreferencesKey("ai_provider")
    val OLLAMA_URL     = stringPreferencesKey("ollama_url")
    val OLLAMA_MODEL   = stringPreferencesKey("ollama_model")
    val SERPER_API_KEY = stringPreferencesKey("serper_api_key")
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.collect { preferences ->
                val providerStr = preferences[PreferencesKeys.AI_PROVIDER] ?: "openai"
                val provider = when (providerStr) {
                    "ollama" -> AiProvider.OLLAMA
                    else     -> AiProvider.OPENAI
                }

                _uiState.update {
                    it.copy(
                        apiKey       = preferences[PreferencesKeys.API_KEY] ?: "",
                        aiProvider   = provider,
                        ollamaUrl    = preferences[PreferencesKeys.OLLAMA_URL]   ?: "",
                        ollamaModel  = preferences[PreferencesKeys.OLLAMA_MODEL] ?: "",
                        serperApiKey = preferences[PreferencesKeys.SERPER_API_KEY] ?: ""
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
                prefs[PreferencesKeys.AI_PROVIDER] = when (provider) {
                    AiProvider.OLLAMA -> "ollama"
                    AiProvider.OPENAI -> "openai"
                }
            }
            _uiState.update { it.copy(aiProvider = provider) }
        }
    }

    // ── Ollama Dev Mode settings ──────────────────────────────────────────────

    fun onOllamaUrlChanged(url: String) =
        _uiState.update { it.copy(ollamaUrl = url, ollamaSavedSuccessfully = false) }

    fun onOllamaModelChanged(model: String) =
        _uiState.update { it.copy(ollamaModel = model, ollamaSavedSuccessfully = false) }

    fun saveOllamaSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOllamaSaving = true) }
            runCatching {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.OLLAMA_URL]   = _uiState.value.ollamaUrl.trim()
                    prefs[PreferencesKeys.OLLAMA_MODEL] = _uiState.value.ollamaModel.trim()
                }
                _uiState.update { it.copy(isOllamaSaving = false, ollamaSavedSuccessfully = true) }
            }.onFailure {
                _uiState.update { it.copy(isOllamaSaving = false) }
            }
        }
    }

    // ── Web Search (Serper.dev) ────────────────────────────────────────────────

    fun onSerperApiKeyChanged(key: String) =
        _uiState.update { it.copy(serperApiKey = key, serperSavedSuccessfully = false) }

    fun saveSerperApiKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSerperSaving = true) }
            runCatching {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.SERPER_API_KEY] = _uiState.value.serperApiKey.trim()
                }
                _uiState.update { it.copy(isSerperSaving = false, serperSavedSuccessfully = true) }
            }.onFailure {
                _uiState.update { it.copy(isSerperSaving = false) }
            }
        }
    }

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
