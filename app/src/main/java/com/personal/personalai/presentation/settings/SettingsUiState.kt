package com.personal.personalai.presentation.settings

data class SettingsUiState(
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val showClearHistoryDialog: Boolean = false
)
