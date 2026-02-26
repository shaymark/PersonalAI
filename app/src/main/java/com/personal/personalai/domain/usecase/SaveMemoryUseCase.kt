package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.MemoryRepository
import javax.inject.Inject

class SaveMemoryUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    suspend operator fun invoke(content: String, topic: String): Memory {
        val memory = Memory(content = content, topic = topic)
        val id = memoryRepository.saveMemory(memory)
        return memory.copy(id = id)
    }
}
