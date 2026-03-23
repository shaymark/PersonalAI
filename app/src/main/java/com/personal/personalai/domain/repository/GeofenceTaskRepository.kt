package com.personal.personalai.domain.repository

import com.personal.personalai.domain.model.GeofenceTask
import kotlinx.coroutines.flow.Flow

interface GeofenceTaskRepository {
    fun getTasks(): Flow<List<GeofenceTask>>
    suspend fun getActiveTasks(): List<GeofenceTask>
    suspend fun getTaskById(id: Long): GeofenceTask?
    suspend fun insertTask(task: GeofenceTask): Long
    suspend fun updateTask(task: GeofenceTask)
    suspend fun deleteTask(task: GeofenceTask)
    suspend fun setActive(id: Long, isActive: Boolean)
}
