package com.personal.personalai.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.usecase.CreateScheduledTaskUseCase
import com.personal.personalai.domain.usecase.DeleteScheduledTaskUseCase
import com.personal.personalai.domain.usecase.GetScheduledTasksUseCase
import com.personal.personalai.domain.usecase.UpdateScheduledTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ScheduledTasksViewModel @Inject constructor(
    private val getScheduledTasksUseCase: GetScheduledTasksUseCase,
    private val createScheduledTaskUseCase: CreateScheduledTaskUseCase,
    private val deleteScheduledTaskUseCase: DeleteScheduledTaskUseCase,
    private val updateScheduledTaskUseCase: UpdateScheduledTaskUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduledTasksUiState(isLoading = true))
    val uiState: StateFlow<ScheduledTasksUiState> = _uiState.asStateFlow()

    init {
        observeTasks()
    }

    private fun observeTasks() {
        viewModelScope.launch {
            getScheduledTasksUseCase().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks, isLoading = false) }
            }
        }
    }

    fun showAddDialog() = _uiState.update {
        it.copy(
            showAddDialog = true,
            newTaskScheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L,
            newTaskType = TaskType.REMINDER,
            newAiPrompt = "",
            newOutputTarget = OutputTarget.NOTIFICATION
        )
    }

    fun dismissAddDialog() = _uiState.update {
        it.copy(
            showAddDialog = false,
            newTaskTitle = "",
            newTaskDescription = "",
            newTaskType = TaskType.REMINDER,
            newAiPrompt = "",
            newOutputTarget = OutputTarget.NOTIFICATION
        )
    }

    fun onTitleChanged(title: String) = _uiState.update { it.copy(newTaskTitle = title) }

    fun onDescriptionChanged(desc: String) = _uiState.update { it.copy(newTaskDescription = desc) }

    fun onScheduledAtChanged(epochMillis: Long) =
        _uiState.update { it.copy(newTaskScheduledAt = epochMillis) }

    fun onTaskTypeChanged(type: TaskType) = _uiState.update { it.copy(newTaskType = type) }

    fun onAiPromptChanged(prompt: String) = _uiState.update { it.copy(newAiPrompt = prompt) }

    fun onOutputTargetChanged(target: OutputTarget) =
        _uiState.update { it.copy(newOutputTarget = target) }

    fun createTask() {
        val state = _uiState.value
        if (state.newTaskTitle.isBlank()) return
        if (state.newTaskType == TaskType.AI_PROMPT && state.newAiPrompt.isBlank()) return
        viewModelScope.launch {
            val isoTime = epochToIso(state.newTaskScheduledAt)
            createScheduledTaskUseCase(
                TaskInfo(
                    title = state.newTaskTitle,
                    description = state.newTaskDescription,
                    scheduledAtIso = isoTime,
                    taskType = state.newTaskType,
                    aiPrompt = state.newAiPrompt.takeIf { it.isNotBlank() },
                    outputTarget = state.newOutputTarget
                )
            )
            _uiState.update {
                it.copy(
                    showAddDialog = false,
                    newTaskTitle = "",
                    newTaskDescription = "",
                    newTaskType = TaskType.REMINDER,
                    newAiPrompt = "",
                    newOutputTarget = OutputTarget.NOTIFICATION
                )
            }
        }
    }

    fun deleteTask(task: ScheduledTask) {
        viewModelScope.launch { deleteScheduledTaskUseCase(task) }
    }

    fun showEditDialog(task: ScheduledTask) = _uiState.update {
        it.copy(
            editingTask        = task,
            newTaskTitle       = task.title,
            newTaskDescription = task.description,
            newTaskScheduledAt = task.scheduledAt,
            newTaskType        = task.taskType,
            newAiPrompt        = task.aiPrompt ?: "",
            newOutputTarget    = task.outputTarget
        )
    }

    fun dismissEditDialog() = _uiState.update {
        it.copy(
            editingTask        = null,
            newTaskTitle       = "",
            newTaskDescription = "",
            newTaskType        = TaskType.REMINDER,
            newAiPrompt        = "",
            newOutputTarget    = OutputTarget.NOTIFICATION
        )
    }

    fun saveEditedTask() {
        val state = _uiState.value
        val task = state.editingTask ?: return
        if (state.newTaskTitle.isBlank()) return
        if (state.newTaskType == TaskType.AI_PROMPT && state.newAiPrompt.isBlank()) return
        if (state.newTaskScheduledAt <= System.currentTimeMillis()) return

        viewModelScope.launch {
            val updated = task.copy(
                title        = state.newTaskTitle.trim(),
                description  = state.newTaskDescription.trim(),
                scheduledAt  = state.newTaskScheduledAt,
                taskType     = state.newTaskType,
                aiPrompt     = state.newAiPrompt.trim().takeIf { it.isNotBlank() },
                outputTarget = state.newOutputTarget
            )
            updateScheduledTaskUseCase(updated)
            dismissEditDialog()
        }
    }

    private fun epochToIso(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
