package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatHistoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Message>> = chatRepository.getMessages()
}
