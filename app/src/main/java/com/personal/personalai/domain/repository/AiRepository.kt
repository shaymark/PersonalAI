package com.personal.personalai.domain.repository

import com.personal.personalai.domain.model.Message

interface AiRepository {
    suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String>
}
