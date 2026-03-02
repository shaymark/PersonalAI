package com.personal.personalai.presentation.chat

import com.personal.personalai.domain.model.Message

/** Three mutually-exclusive voice-input states. Using a single enum prevents
 *  illegal combinations such as recording + transcribing simultaneously. */
enum class VoiceState { IDLE, RECORDING, TRANSCRIBING }

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val voiceState: VoiceState = VoiceState.IDLE
)
