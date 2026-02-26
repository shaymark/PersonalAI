package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMemoriesUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    operator fun invoke(): Flow<List<Memory>> = memoryRepository.getMemories()
}
