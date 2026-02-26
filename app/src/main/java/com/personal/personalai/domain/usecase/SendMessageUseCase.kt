package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.model.SendMessageResult
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val createScheduledTaskUseCase: CreateScheduledTaskUseCase
) {
    suspend operator fun invoke(userMessage: String): Result<SendMessageResult> {
        val userMsg = Message(content = userMessage, role = MessageRole.USER)
        chatRepository.saveMessage(userMsg)

        val chatHistory = chatRepository.getMessages().first().takeLast(10)
        val aiResult = aiRepository.sendMessage(userMessage, chatHistory)

        return aiResult.map { responseText ->
            val taskInfo = parseTaskFromResponse(responseText)
            val cleanResponse = removeTaskTagFromResponse(responseText)

            val createdTask = if (taskInfo != null) {
                createScheduledTaskUseCase(taskInfo)
            } else null

            val aiMsg = Message(
                content = cleanResponse,
                role = MessageRole.ASSISTANT,
                hasCreatedTask = createdTask != null,
                createdTaskTitle = createdTask?.title
            )
            chatRepository.saveMessage(aiMsg)

            SendMessageResult(message = aiMsg, createdTask = createdTask)
        }
    }

    private fun parseTaskFromResponse(response: String): TaskInfo? {
        val tagRegex = Regex("""\[TASK:(\{[^\]]*\})\]""")
        val match = tagRegex.find(response) ?: return null
        val json = match.groupValues[1]
        val title = extractJsonString(json, "title") ?: return null
        val description = extractJsonString(json, "description") ?: ""
        val scheduledAt = extractJsonString(json, "scheduledAt") ?: return null
        return TaskInfo(title = title, description = description, scheduledAtIso = scheduledAt)
    }

    private fun extractJsonString(json: String, field: String): String? {
        val pattern = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun removeTaskTagFromResponse(response: String): String {
        return response.replace(Regex("""\s*\[TASK:\{[^\]]*\}\]"""), "").trim()
    }
}
