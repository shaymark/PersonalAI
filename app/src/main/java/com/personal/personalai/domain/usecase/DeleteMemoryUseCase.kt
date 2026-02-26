package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.MemoryRepository
import javax.inject.Inject

class DeleteMemoryUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    suspend operator fun invoke(memory: Memory) = memoryRepository.deleteMemory(memory)
}
