package com.personal.personalai.data.repository

import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.repository.AiRepository
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor() : AiRepository {

    override suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        delay(1200L) // Simulate network latency
        val lower = message.lowercase()
        return when {
            lower.containsAny("remind me", "set a reminder", "schedule a", "don't forget", "notify me") ->
                generateTaskResponse(message)

            lower.containsAny("hello", "hi", "hey") ->
                Result.success(
                    "Hello! I'm your personal AI assistant. I can answer questions, " +
                    "have conversations, and schedule reminders for you. " +
                    "Just say \"remind me to...\" and I'll create a scheduled task automatically!"
                )

            lower.containsAny("help", "what can you do", "capabilities") ->
                Result.success(
                    "Here's what I can do:\n\n" +
                    "• Answer your questions on any topic\n" +
                    "• Have natural conversations\n" +
                    "• Schedule reminders automatically — just say \"remind me to...\"\n" +
                    "• Accept voice input — tap the microphone button\n\n" +
                    "How can I assist you today?"
                )

            lower.containsAny("weather", "temperature", "forecast") ->
                Result.success(
                    "I don't have access to real-time weather data. " +
                    "I can remind you to check the forecast though — " +
                    "just say \"remind me to check the weather at 7am\"!"
                )

            lower.containsAny("thank", "thanks") ->
                Result.success("You're welcome! Is there anything else I can help you with?")

            else -> Result.success(generateGenericResponse(message))
        }
    }

    private fun generateTaskResponse(message: String): Result<String> {
        val taskTitle = extractTaskTitle(message)
        val scheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L // default: 1 hour
        val isoTime = formatEpochToIso(scheduledAt)

        val response = "Sure! I've scheduled a reminder: \"$taskTitle\". " +
            "You'll receive a notification in about 1 hour.\n" +
            "[TASK:{\"title\":\"$taskTitle\",\"description\":\"Reminder from your AI assistant\",\"scheduledAt\":\"$isoTime\"}]"
        return Result.success(response)
    }

    private fun extractTaskTitle(message: String): String {
        val patterns = listOf(
            Regex("remind me to (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("remind me about (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("remind me (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("schedule a (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("set a reminder (?:for|to) (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("don't forget to (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("notify me (?:to|about|when) (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1].trim().removeSuffix(".").removeSuffix("!").trim()
            }
        }
        return "Scheduled task"
    }

    private fun formatEpochToIso(epochMillis: Long): String {
        return java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }

    private fun generateGenericResponse(message: String): String {
        val responses = listOf(
            "That's an interesting topic! I'm processing your message. " +
                "Feel free to ask follow-up questions — or say \"remind me to...\" to schedule a task.",
            "I understand. As your AI assistant, I'm here to help with questions and reminders. " +
                "What would you like to explore further?",
            "Great question! I'd be happy to dig into that with you. " +
                "You can also ask me to schedule reminders by saying \"remind me to...\"",
            "I see what you mean. Could you tell me more so I can give you a better answer? " +
                "I'm always learning from our conversations.",
            "Noted! I can help you think through this. " +
                "Also remember you can tap the microphone to speak your messages instead of typing."
        )
        return responses[message.length % responses.size]
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }
}
