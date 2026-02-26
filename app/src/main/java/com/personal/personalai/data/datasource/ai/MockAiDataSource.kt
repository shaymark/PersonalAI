package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Memory
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

    fun sendMessage(message: String, chatHistory: List<Message>, memories: List<Memory>): Result<String> {
        val lower = message.lowercase()
        return when {
            // Memory — save
            lower.containsAny("remember that", "remember my", "remember i ", "keep in mind", "note that", "don't forget that") ->
                generateMemoryResponse(message)

            // Memory — forget by topic
            lower.containsAny("forget my", "forget about my") && !lower.contains("forget everything") ->
                generateForgetResponse(message)

            // Memory — forget all
            lower.containsAny("forget everything", "clear all my memories", "forget all") ->
                Result.success(
                    "Done! I've cleared everything I remember about you.\n[FORGET_ALL]"
                )

            // Task scheduling
            lower.containsAny("remind me", "set a reminder", "schedule a", "don't forget to", "notify me") ->
                generateTaskResponse(message)

            lower.containsAny("hello", "hi", "hey") ->
                Result.success(
                    "Hello! I'm your personal AI assistant running in mock mode.\n\n" +
                    "To enable real AI responses, add your OpenAI API key in ⚙️ Settings.\n\n" +
                    "I can also schedule reminders and remember things for you!"
                )

            lower.containsAny("what do you remember", "what do you know about me") ->
                if (memories.isEmpty()) {
                    Result.success("I don't have any memories about you yet. Tell me something to remember!")
                } else {
                    val list = memories.joinToString("\n") { "• [${it.topic}] ${it.content}" }
                    Result.success("Here's what I remember about you:\n\n$list")
                }

            lower.containsAny("help", "what can you do", "capabilities") ->
                Result.success(
                    "Here's what I can do:\n\n" +
                    "• Answer your questions — add your OpenAI key in Settings for real answers\n" +
                    "• Schedule reminders — say \"remind me to...\"\n" +
                    "• Remember things — say \"remember that my name is...\"\n" +
                    "• Forget things — say \"forget my name\" or \"forget everything\"\n" +
                    "• Accept voice input — tap the 🎤 button"
                )

            lower.containsAny("thank", "thanks") ->
                Result.success("You're welcome! Is there anything else I can help you with?")

            lower.containsAny("weather", "temperature", "forecast") ->
                Result.success(
                    "I don't have access to live weather data in mock mode. " +
                    "I can schedule a reminder to check for you — say \"remind me to check the weather at 7am\"!"
                )

            lower.containsAny(
                "search for", "search the web", "look up", "look it up",
                "check online", "find on the internet", "google",
                "what's happening", "latest news", "current events", "browse"
            ) ->
                Result.success(
                    "I'd love to help search the web, but I'm running in offline/mock mode. " +
                    "Please add your OpenAI API key in \u2699\uFE0F Settings to enable real web search."
                )

            else ->
                Result.success(
                    "Got your message! (Running in mock mode — add your OpenAI API key in " +
                    "⚙️ Settings for real AI responses.)"
                )
        }
    }

    private fun generateMemoryResponse(message: String): Result<String> {
        val content = extractMemoryContent(message)
        val topic = inferTopic(message)
        return Result.success(
            "Got it! I'll remember: \"$content\".\n" +
            "[MEMORY:{\"content\":\"$content\",\"topic\":\"$topic\"}]"
        )
    }

    private fun generateForgetResponse(message: String): Result<String> {
        val topic = extractForgetTopic(message)
        return Result.success(
            "Done! I've forgotten everything I knew about \"$topic\".\n" +
            "[FORGET:{\"topic\":\"$topic\"}]"
        )
    }

    private fun extractMemoryContent(message: String): String {
        val patterns = listOf(
            Regex("remember that (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("remember my (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("remember i (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("keep in mind (?:that )?(.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("note that (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("don't forget that (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) return match.groupValues[1].trim()
        }
        return message.trim()
    }

    private fun extractForgetTopic(message: String): String {
        val patterns = listOf(
            Regex("forget my (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
            Regex("forget about my (.+?)(?:\\.|!|\\?|$)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) return match.groupValues[1].trim()
        }
        return "general"
    }

    private fun inferTopic(message: String): String = when {
        message.contains("name", ignoreCase = true) -> "name"
        message.contains("prefer", ignoreCase = true) -> "preference"
        message.contains("like", ignoreCase = true) -> "preference"
        message.contains("job", ignoreCase = true) || message.contains("work", ignoreCase = true) -> "work"
        message.contains("age", ignoreCase = true) || message.contains("born", ignoreCase = true) -> "personal"
        else -> "general"
    }

    private fun generateTaskResponse(message: String): Result<String> {
        val title = extractTaskTitle(message)
        val scheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L
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
            if (match != null) return match.groupValues[1].trim().removeSuffix(".").removeSuffix("!").trim()
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
