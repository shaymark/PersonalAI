package com.personal.personalai.domain.repository

import com.personal.personalai.domain.model.Message
import java.io.File

interface AiRepository {
    suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String>
    suspend fun transcribeAudio(audioFile: File): Result<String>
}
