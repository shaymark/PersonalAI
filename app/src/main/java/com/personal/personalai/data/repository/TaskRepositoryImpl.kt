package com.personal.personalai.data.repository

import com.personal.personalai.data.local.dao.ScheduledTaskDao
import com.personal.personalai.data.local.entity.toDomain
import com.personal.personalai.data.local.entity.toEntity
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val scheduledTaskDao: ScheduledTaskDao
) : TaskRepository {

    override fun getTasks(): Flow<List<ScheduledTask>> =
        scheduledTaskDao.getAllTasks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertTask(task: ScheduledTask): Long =
        scheduledTaskDao.insertTask(task.toEntity())

    override suspend fun deleteTask(task: ScheduledTask) =
        scheduledTaskDao.deleteTask(task.toEntity())

    override suspend fun markCompleted(taskId: Long) =
        scheduledTaskDao.markCompleted(taskId)

    override suspend fun updateWorkerId(taskId: Long, workerId: String) =
        scheduledTaskDao.updateWorkerId(taskId, workerId)
}
