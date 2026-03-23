package com.personal.personalai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.model.GeofenceTransitionType
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.TaskType

@Entity(tableName = "geofence_tasks")
data class GeofenceTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val locationName: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val transitionType: String = GeofenceTransitionType.ENTER.name,
    val taskType: String = TaskType.REMINDER.name,
    val description: String = "",
    val aiPrompt: String? = null,
    val outputTarget: String = OutputTarget.NOTIFICATION.name,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

fun GeofenceTaskEntity.toDomain() = GeofenceTask(
    id = id,
    title = title,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    transitionType = runCatching { GeofenceTransitionType.valueOf(transitionType) }.getOrDefault(GeofenceTransitionType.ENTER),
    taskType = runCatching { TaskType.valueOf(taskType) }.getOrDefault(TaskType.REMINDER),
    description = description,
    aiPrompt = aiPrompt,
    outputTarget = runCatching { OutputTarget.valueOf(outputTarget) }.getOrDefault(OutputTarget.NOTIFICATION),
    isActive = isActive,
    createdAt = createdAt
)

fun GeofenceTask.toEntity() = GeofenceTaskEntity(
    id = id,
    title = title,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    transitionType = transitionType.name,
    taskType = taskType.name,
    description = description,
    aiPrompt = aiPrompt,
    outputTarget = outputTarget.name,
    isActive = isActive,
    createdAt = createdAt
)
