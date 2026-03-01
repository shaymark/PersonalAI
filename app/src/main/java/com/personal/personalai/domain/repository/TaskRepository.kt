package com.personal.personalai.domain.repository

import com.personal.personalai.domain.model.ScheduledTask
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getTasks(): Flow<List<ScheduledTask>>
    suspend fun insertTask(task: ScheduledTask): Long
    suspend fun deleteTask(task: ScheduledTask)
    suspend fun markCompleted(taskId: Long)
    suspend fun updateTask(task: ScheduledTask)
    suspend fun updateWorkerId(taskId: Long, workerId: String)
    suspend fun updateNextOccurrence(taskId: Long, scheduledAt: Long, workerId: String)
}
