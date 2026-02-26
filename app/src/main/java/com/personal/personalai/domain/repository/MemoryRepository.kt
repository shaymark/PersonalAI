package com.personal.personalai.domain.repository

import com.personal.personalai.domain.model.Memory
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getMemories(): Flow<List<Memory>>
    suspend fun saveMemory(memory: Memory): Long
    suspend fun deleteMemory(memory: Memory)
    suspend fun deleteByTopic(topic: String)
    suspend fun clearAllMemories()
}
