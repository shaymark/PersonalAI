package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.repository.MemoryRepository
import javax.inject.Inject

class ForgetByTopicUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    suspend operator fun invoke(topic: String) = memoryRepository.deleteByTopic(topic)
}
