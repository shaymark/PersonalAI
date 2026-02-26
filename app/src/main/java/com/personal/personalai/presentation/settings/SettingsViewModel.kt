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
    val API_KEY = stringPreferencesKey("api_key")
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

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.collect { preferences ->
                _uiState.update { it.copy(apiKey = preferences[PreferencesKeys.API_KEY] ?: "") }
            }
        }
    }

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

    fun showClearHistoryDialog() = _uiState.update { it.copy(showClearHistoryDialog = true) }

    fun dismissClearHistoryDialog() = _uiState.update { it.copy(showClearHistoryDialog = false) }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
            _uiState.update { it.copy(showClearHistoryDialog = false) }
        }
    }
}
