package com.personal.personalai.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.usecase.CreateScheduledTaskUseCase
import com.personal.personalai.domain.usecase.DeleteScheduledTaskUseCase
import com.personal.personalai.domain.usecase.GetScheduledTasksUseCase
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
    private val deleteScheduledTaskUseCase: DeleteScheduledTaskUseCase
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
            newTaskScheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L
        )
    }

    fun dismissAddDialog() = _uiState.update {
        it.copy(showAddDialog = false, newTaskTitle = "", newTaskDescription = "")
    }

    fun onTitleChanged(title: String) = _uiState.update { it.copy(newTaskTitle = title) }

    fun onDescriptionChanged(desc: String) = _uiState.update { it.copy(newTaskDescription = desc) }

    fun onScheduledAtChanged(epochMillis: Long) =
        _uiState.update { it.copy(newTaskScheduledAt = epochMillis) }

    fun createTask() {
        val state = _uiState.value
        if (state.newTaskTitle.isBlank()) return
        viewModelScope.launch {
            val isoTime = epochToIso(state.newTaskScheduledAt)
            createScheduledTaskUseCase(
                TaskInfo(
                    title = state.newTaskTitle,
                    description = state.newTaskDescription,
                    scheduledAtIso = isoTime
                )
            )
            _uiState.update { it.copy(showAddDialog = false, newTaskTitle = "", newTaskDescription = "") }
        }
    }

    fun deleteTask(task: ScheduledTask) {
        viewModelScope.launch { deleteScheduledTaskUseCase(task) }
    }

    private fun epochToIso(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
