package com.personal.personalai.domain.model

enum class TaskType { REMINDER, AI_PROMPT }

enum class OutputTarget { NOTIFICATION, CHAT, BOTH }

enum class RecurrenceType { NONE, DAILY, WEEKLY }

data class ScheduledTask(
    val id: Long = 0,
    val title: String,
    val description: String,
    val scheduledAt: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val workerId: String? = null,
    val taskType: TaskType = TaskType.REMINDER,
    val aiPrompt: String? = null,
    val outputTarget: OutputTarget = OutputTarget.NOTIFICATION,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE
)
