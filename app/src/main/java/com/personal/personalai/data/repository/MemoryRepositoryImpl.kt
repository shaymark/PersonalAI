package com.personal.personalai.data.repository

import com.personal.personalai.data.local.dao.MemoryDao
import com.personal.personalai.data.local.entity.toDomain
import com.personal.personalai.data.local.entity.toEntity
import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun getMemories(): Flow<List<Memory>> =
        memoryDao.getAllMemories().map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveMemory(memory: Memory): Long =
        memoryDao.insertMemory(memory.toEntity())

    override suspend fun deleteMemory(memory: Memory) =
        memoryDao.deleteMemory(memory.toEntity())

    override suspend fun deleteByTopic(topic: String) =
        memoryDao.deleteByTopic(topic)

    override suspend fun clearAllMemories() =
        memoryDao.clearAll()
}
