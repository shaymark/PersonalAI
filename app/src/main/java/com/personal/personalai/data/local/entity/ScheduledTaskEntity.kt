package com.personal.personalai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskType

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val scheduledAt: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val workerId: String? = null,
    val taskType: String = TaskType.REMINDER.name,
    val aiPrompt: String? = null,
    val outputTarget: String = OutputTarget.NOTIFICATION.name
)

fun ScheduledTaskEntity.toDomain() = ScheduledTask(
    id = id,
    title = title,
    description = description,
    scheduledAt = scheduledAt,
    isCompleted = isCompleted,
    createdAt = createdAt,
    workerId = workerId,
    taskType = runCatching { TaskType.valueOf(taskType) }.getOrDefault(TaskType.REMINDER),
    aiPrompt = aiPrompt,
    outputTarget = runCatching { OutputTarget.valueOf(outputTarget) }.getOrDefault(OutputTarget.NOTIFICATION)
)

fun ScheduledTask.toEntity() = ScheduledTaskEntity(
    id = id,
    title = title,
    description = description,
    scheduledAt = scheduledAt,
    isCompleted = isCompleted,
    createdAt = createdAt,
    workerId = workerId,
    taskType = taskType.name,
    aiPrompt = aiPrompt,
    outputTarget = outputTarget.name
)
