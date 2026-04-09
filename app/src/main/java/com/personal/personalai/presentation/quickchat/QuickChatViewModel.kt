package com.personal.personalai.presentation.quickchat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.audio.AudioRecorder
import com.personal.personalai.domain.tools.PermissionBroker
import com.personal.personalai.domain.tools.UserInputBroker
import com.personal.personalai.domain.usecase.AgentLoopUseCase
import com.personal.personalai.domain.usecase.AgentStep
import com.personal.personalai.domain.usecase.TranscribeAudioUseCase
import com.personal.personalai.presentation.chat.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickChatViewModel @Inject constructor(
    private val agentLoopUseCase: AgentLoopUseCase,
    private val userInputBroker: UserInputBroker,
    private val permissionBroker: PermissionBroker,
    private val audioRecorder: AudioRecorder,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val inputText: String = "",
        val isLoading: Boolean = false,
        val response: String? = null,
        val statusMessage: String? = null,
        val pendingInputRequest: UserInputBroker.Request? = null,
        val pendingPermissionRequest: PermissionBroker.Request? = null,
        val voiceState: VoiceState = VoiceState.IDLE,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeUserInputRequests()
        observePermissionRequests()
    }

    private fun observeUserInputRequests() {
        viewModelScope.launch {
            userInputBroker.incoming.collect { request ->
                _uiState.update {
                    it.copy(
                        pendingInputRequest = request,
                        statusMessage = "❓ ${request.question}"
                    )
                }
            }
        }
    }

    private fun observePermissionRequests() {
        viewModelScope.launch {
            permissionBroker.incoming.collect { request ->
                _uiState.update { it.copy(pendingPermissionRequest = request) }
            }
        }
    }

    fun resolvePermission(requestId: String, granted: Boolean) {
        _uiState.update { it.copy(pendingPermissionRequest = null) }
        permissionBroker.resolve(requestId, granted)
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** Called when the user taps a quick-reply button. */
    fun answerQuickReply(reply: String) {
        val pending = _uiState.value.pendingInputRequest ?: return
        _uiState.update { it.copy(pendingInputRequest = null, statusMessage = "Thinking…") }
        userInputBroker.answer(pending.id, reply)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val pending = _uiState.value.pendingInputRequest
        if (pending != null) {
            _uiState.update { it.copy(inputText = "", pendingInputRequest = null, statusMessage = "Thinking…") }
            userInputBroker.answer(pending.id, text)
            return
        }

        if (_uiState.value.isLoading) return

        _uiState.update {
            it.copy(
                inputText = "",
                isLoading = true,
                response = null,
                statusMessage = "Thinking…",
                error = null
            )
        }

        viewModelScope.launch {
            agentLoopUseCase(text, backgroundMode = false).collect { step ->
                when (step) {
                    is AgentStep.ToolCalling ->
                        _uiState.update { it.copy(statusMessage = step.humanReadable) }
                    is AgentStep.Complete -> {
                        step.result
                            .onSuccess { responseText ->
                                _uiState.update {
                                    it.copy(isLoading = false, statusMessage = null, response = responseText)
                                }
                            }
                            .onFailure { e ->
                                _uiState.update {
                                    it.copy(isLoading = false, statusMessage = null, error = e.message ?: "Something went wrong")
                                }
                            }
                    }
                }
            }
        }
    }

    // ── Voice recording ───────────────────────────────────────────────────────

    fun onRecordStart() {
        if (_uiState.value.voiceState != VoiceState.IDLE) return
        try {
            audioRecorder.start()
            _uiState.update { it.copy(voiceState = VoiceState.RECORDING) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _uiState.update { it.copy(error = "Recording failed: ${e.message}") }
        }
    }

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

    fun onRecordCancel() {
        if (_uiState.value.voiceState != VoiceState.RECORDING) return
        audioRecorder.stop()?.delete()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
    }

    fun onMicPermissionDenied() {
        _uiState.update { it.copy(error = "Microphone permission is required for voice input") }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
    }

    companion object {
        private const val TAG = "QuickChatViewModel"
    }
}
