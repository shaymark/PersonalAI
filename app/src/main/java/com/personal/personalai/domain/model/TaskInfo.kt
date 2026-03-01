package com.personal.personalai.domain.model

data class TaskInfo(
    val title: String,
    val description: String,
    val scheduledAtIso: String,
    val taskType: TaskType = TaskType.REMINDER,
    val aiPrompt: String? = null,
    val outputTarget: OutputTarget = OutputTarget.NOTIFICATION
)
