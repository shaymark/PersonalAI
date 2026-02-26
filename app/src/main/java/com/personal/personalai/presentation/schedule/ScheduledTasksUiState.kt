package com.personal.personalai.presentation.schedule

import com.personal.personalai.domain.model.ScheduledTask

data class ScheduledTasksUiState(
    val tasks: List<ScheduledTask> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val newTaskTitle: String = "",
    val newTaskDescription: String = "",
    val newTaskScheduledAt: Long = System.currentTimeMillis() + 60 * 60 * 1000L,
    val error: String? = null
)
