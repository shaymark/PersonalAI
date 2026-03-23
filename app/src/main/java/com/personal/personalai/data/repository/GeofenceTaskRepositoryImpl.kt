package com.personal.personalai.data.repository

import com.personal.personalai.data.local.dao.GeofenceTaskDao
import com.personal.personalai.data.local.entity.toDomain
import com.personal.personalai.data.local.entity.toEntity
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.repository.GeofenceTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GeofenceTaskRepositoryImpl @Inject constructor(
    private val dao: GeofenceTaskDao
) : GeofenceTaskRepository {

    override fun getTasks(): Flow<List<GeofenceTask>> =
        dao.getAllTasks().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveTasks(): List<GeofenceTask> =
        dao.getActiveTasks().map { it.toDomain() }

    override suspend fun getTaskById(id: Long): GeofenceTask? =
        dao.getTaskById(id)?.toDomain()

    override suspend fun insertTask(task: GeofenceTask): Long =
        dao.insertTask(task.toEntity())

    override suspend fun updateTask(task: GeofenceTask) =
        dao.updateTask(task.toEntity())

    override suspend fun deleteTask(task: GeofenceTask) =
        dao.deleteTask(task.toEntity())

    override suspend fun setActive(id: Long, isActive: Boolean) =
        dao.setActive(id, isActive)
}
