package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Message
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Returns pre-defined responses for all common intents.
 * Used when no OpenAI API key is configured.
 * Has no external dependencies — pure, deterministic, and fully unit-testable.
 */
class MockAiDataSource @Inject constructor() {

    fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        val lower = message.lowercase()
        return when {
            lower.containsAny("remind me", "set a reminder", "schedule a", "don't forget", "notify me") ->
                generateTaskResponse(message)

            lower.containsAny("hello", "hi", "hey") ->
                Result.success(
                    "Hello! I'm your personal AI assistant running in mock mode.\n\n" +
                    "To enable real AI responses, add your OpenAI API key in ⚙️ Settings.\n\n" +
                    "I can also schedule reminders for you — just say \"remind me to…\"!"
                )

            lower.containsAny("help", "what can you do", "capabilities") ->
                Result.success(
                    "Here's what I can do:\n\n" +
                    "• Answer your questions — add your OpenAI key in Settings for real answers\n" +
                    "• Have natural conversations\n" +
                    "• Schedule reminders automatically — just say \"remind me to...\"\n" +
                    "• Accept voice input — tap the 🎤 button"
                )

            lower.containsAny("thank", "thanks") ->
                Result.success("You're welcome! Is there anything else I can help you with?")

            lower.containsAny("weather", "temperature", "forecast") ->
                Result.success(
                    "I don't have access to live weather data in mock mode. " +
                    "I can schedule a reminder to check for you though — " +
                    "just say \"remind me to check the weather at 7am\"!"
                )

            else ->
                Result.success(
                    "Got your message! (Running in mock mode — add your OpenAI API key in " +
                    "⚙️ Settings for real AI responses.)"
                )
        }
    }

    private fun generateTaskResponse(message: String): Result<String> {
        val title = extractTaskTitle(message)
        val scheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L // default: 1 hour
        val isoTime = formatEpochToIso(scheduledAt)
        val response =
            "Sure! I've scheduled a reminder: \"$title\". " +
            "You'll receive a notification in about 1 hour.\n" +
            "[TASK:{\"title\":\"$title\"," +
            "\"description\":\"Reminder from your AI assistant\"," +
            "\"scheduledAt\":\"$isoTime\"}]"
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

    private fun formatEpochToIso(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }
}
