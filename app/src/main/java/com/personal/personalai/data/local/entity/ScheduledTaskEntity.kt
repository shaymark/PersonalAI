package com.personal.personalai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.personalai.domain.model.ScheduledTask

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val scheduledAt: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val workerId: String? = null
)

fun ScheduledTaskEntity.toDomain() = ScheduledTask(
    id = id,
    title = title,
    description = description,
    scheduledAt = scheduledAt,
    isCompleted = isCompleted,
    createdAt = createdAt,
    workerId = workerId
)

fun ScheduledTask.toEntity() = ScheduledTaskEntity(
    id = id,
    title = title,
    description = description,
    scheduledAt = scheduledAt,
    isCompleted = isCompleted,
    createdAt = createdAt,
    workerId = workerId
)
