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
    private val createScheduledTaskUseCase: CreateScheduledTaskUseCase,
    private val saveMemoryUseCase: SaveMemoryUseCase,
    private val forgetByTopicUseCase: ForgetByTopicUseCase,
    private val clearAllMemoriesUseCase: ClearAllMemoriesUseCase
) {
    suspend operator fun invoke(userMessage: String): Result<SendMessageResult> {
        val userMsg = Message(content = userMessage, role = MessageRole.USER)
        chatRepository.saveMessage(userMsg)

        val chatHistory = chatRepository.getMessages().first().takeLast(10)
        val aiResult = aiRepository.sendMessage(userMessage, chatHistory)

        return aiResult.map { responseText ->
            // Parse all machine-readable tags from the response
            val taskInfo = parseTaskFromResponse(responseText)
            val memoryInfo = parseMemoryFromResponse(responseText)
            val forgetTopic = parseForgetTopicFromResponse(responseText)
            val forgetAll = hasForgetAll(responseText)

            // Strip all tags from the visible message
            val cleanResponse = removeAllTagsFromResponse(responseText)

            // Act on task tag
            val createdTask = if (taskInfo != null) {
                createScheduledTaskUseCase(taskInfo)
            } else null

            // Act on memory tags
            when {
                forgetAll -> clearAllMemoriesUseCase()
                forgetTopic != null -> forgetByTopicUseCase(forgetTopic)
                memoryInfo != null -> saveMemoryUseCase(memoryInfo.content, memoryInfo.topic)
            }

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

    // ─── Tag parsers ──────────────────────────────────────────────────────────

    private fun parseTaskFromResponse(response: String): TaskInfo? {
        val tagRegex = Regex("""\[TASK:(\{[^\]]*\})\]""")
        val match = tagRegex.find(response) ?: return null
        val json = match.groupValues[1]
        val title = extractJsonString(json, "title") ?: return null
        val description = extractJsonString(json, "description") ?: ""
        val scheduledAt = extractJsonString(json, "scheduledAt") ?: return null
        return TaskInfo(title = title, description = description, scheduledAtIso = scheduledAt)
    }

    private fun parseMemoryFromResponse(response: String): MemoryInfo? {
        val tagRegex = Regex("""\[MEMORY:(\{[^\]]*\})\]""")
        val match = tagRegex.find(response) ?: return null
        val json = match.groupValues[1]
        val content = extractJsonString(json, "content") ?: return null
        val topic = extractJsonString(json, "topic") ?: "general"
        return MemoryInfo(content = content, topic = topic)
    }

    private fun parseForgetTopicFromResponse(response: String): String? {
        val tagRegex = Regex("""\[FORGET:(\{[^\]]*\})\]""")
        val match = tagRegex.find(response) ?: return null
        val json = match.groupValues[1]
        return extractJsonString(json, "topic")
    }

    private fun hasForgetAll(response: String): Boolean =
        response.contains("[FORGET_ALL]")

    // ─── Tag stripping ────────────────────────────────────────────────────────

    private fun removeAllTagsFromResponse(response: String): String =
        response
            .replace(Regex("""\s*\[TASK:\{[^\]]*\}\]"""), "")
            .replace(Regex("""\s*\[MEMORY:\{[^\]]*\}\]"""), "")
            .replace(Regex("""\s*\[FORGET:\{[^\]]*\}\]"""), "")
            .replace(Regex("""\s*\[FORGET_ALL\]"""), "")
            .trim()

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun extractJsonString(json: String, field: String): String? {
        val pattern = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private data class MemoryInfo(val content: String, val topic: String)
}
