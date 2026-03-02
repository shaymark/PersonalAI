package com.personal.personalai.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.audio.AudioRecorder
import com.personal.personalai.domain.usecase.GetChatHistoryUseCase
import com.personal.personalai.domain.usecase.SendMessageUseCase
import com.personal.personalai.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getChatHistoryUseCase: GetChatHistoryUseCase,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            getChatHistoryUseCase().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading) return

        _uiState.update { it.copy(inputText = "", isLoading = true, error = null) }

        viewModelScope.launch {
            sendMessageUseCase(text)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    // ── Voice recording ──────────────────────────────────────────────────────

    /** Called when the user presses and holds the mic button. */
    fun onRecordStart() {
        if (_uiState.value.voiceState != VoiceState.IDLE) return
        try {
            audioRecorder.start()
            _uiState.update { it.copy(voiceState = VoiceState.RECORDING) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _uiState.update { it.copy(error = "Failed to start recording: ${e.message}") }
        }
    }

    /** Called when the user releases the mic button — transcribes the recording. */
    fun onRecordStop() {
        if (_uiState.value.voiceState != VoiceState.RECORDING) return
        val file = audioRecorder.stop() ?: run {
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            return
        }
        _uiState.update { it.copy(voiceState = VoiceState.TRANSCRIBING) }
        viewModelScope.launch {
            transcribeAudioUseCase(file)
                .onSuccess { text ->
                    _uiState.update { it.copy(voiceState = VoiceState.IDLE, inputText = text) }
                }
                .onFailure { e ->
                    Log.e(TAG, "Transcription failed: ${e.message}", e)
                    _uiState.update { it.copy(voiceState = VoiceState.IDLE, error = e.message) }
                }
            file.delete()
        }
    }

    /** Called when the user drags their finger off the mic button — discards the recording. */
    fun onRecordCancel() {
        if (_uiState.value.voiceState != VoiceState.RECORDING) return
        audioRecorder.stop()?.delete()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
    }

    /** Called when the runtime microphone permission is denied by the user. */
    fun onMicPermissionDenied() {
        _uiState.update { it.copy(error = "Microphone permission is required for voice input.") }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
