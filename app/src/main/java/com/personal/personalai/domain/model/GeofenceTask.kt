package com.personal.personalai.domain.model

enum class GeofenceTransitionType { ENTER, EXIT, BOTH }

data class GeofenceTask(
    val id: Long = 0,
    val title: String,
    val locationName: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val transitionType: GeofenceTransitionType = GeofenceTransitionType.ENTER,
    val taskType: TaskType = TaskType.REMINDER,
    val description: String = "",
    val aiPrompt: String? = null,
    val outputTarget: OutputTarget = OutputTarget.NOTIFICATION,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
