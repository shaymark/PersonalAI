package com.personal.personalai.domain.model

data class ScheduledTask(
    val id: Long = 0,
    val title: String,
    val description: String,
    val scheduledAt: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val workerId: String? = null
)
