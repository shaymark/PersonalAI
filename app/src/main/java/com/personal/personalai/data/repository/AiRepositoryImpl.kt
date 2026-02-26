package com.personal.personalai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.presentation.settings.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>
) : AiRepository {

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT = """
            You are a helpful personal AI assistant running on an Android app.
            You can answer questions and have natural conversations.

            IMPORTANT: When the user wants to schedule a reminder or task (e.g. "remind me to...",
            "schedule a...", "set a reminder for...", "don't forget to...", "notify me..."),
            respond helpfully AND append this exact machine-readable tag at the very END of your
            response, on its own line, with no trailing spaces:
            [TASK:{"title":"<short task title>","description":"<brief description>","scheduledAt":"<ISO datetime like 2024-01-15T14:30:00>"}]

            For scheduledAt, infer the time from context (e.g. "tomorrow at 9am", "in 2 hours").
            Default to 1 hour from now if no time is specified.
            Current date and time: {{DATETIME}}

            For all other messages, respond normally — do NOT include any task tag.
        """.trimIndent()
    }

    override suspend fun sendMessage(message: String, chatHistory: List<Message>): Result<String> {
        val apiKey = dataStore.data.first()[PreferencesKeys.API_KEY].orEmpty()
        return if (apiKey.isBlank()) {
            mockResponse(message)
        } else {
            callOpenAi(apiKey, chatHistory)
        }
    }

    // ─── Real OpenAI call ────────────────────────────────────────────────────

    private suspend fun callOpenAi(
        apiKey: String,
        chatHistory: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val now = formatEpochToIso(System.currentTimeMillis())
            val systemPrompt = SYSTEM_PROMPT.replace("{{DATETIME}}", now)

            val messages = JSONArray()

            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            // Full chat history (already includes the current user message at the end)
            // Limit to last 20 messages to stay within context limits
            chatHistory.takeLast(20).forEach { msg ->
                messages.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.content)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 500)
            }

            val request = Request.Builder()
                .url(OPENAI_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response from OpenAI"))

            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                }.getOrDefault("OpenAI error: HTTP ${response.code}")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val content = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            Result.success(content.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Mock responses (used when no API key is set) ────────────────────────

    private fun mockResponse(message: String): Result<String> {
        val lower = message.lowercase()
        return when {
            lower.containsAny("remind me", "set a reminder", "schedule a", "don't forget", "notify me") ->
                generateMockTaskResponse(message)

            lower.containsAny("hello", "hi", "hey") ->
                Result.success(
                    "Hello! I'm your personal AI assistant (mock mode).\n" +
                    "Add your OpenAI API key in Settings to enable real AI responses.\n\n" +
                    "I can answer questions and schedule reminders — just say \"remind me to...\"!"
                )

            lower.containsAny("help", "what can you do", "capabilities") ->
                Result.success(
                    "Here's what I can do:\n\n" +
                    "• Answer your questions (add your OpenAI key in Settings for real answers)\n" +
                    "• Have natural conversations\n" +
                    "• Schedule reminders automatically — just say \"remind me to...\"\n" +
                    "• Accept voice input — tap the microphone button"
                )

            lower.containsAny("thank", "thanks") ->
                Result.success("You're welcome! Is there anything else I can help you with?")

            else ->
                Result.success(
                    "I received your message (mock mode). For real AI responses, " +
                    "add your OpenAI API key in Settings."
                )
        }
    }

    private fun generateMockTaskResponse(message: String): Result<String> {
        val taskTitle = extractTaskTitle(message)
        val scheduledAt = System.currentTimeMillis() + 60 * 60 * 1000L // 1 hour
        val isoTime = formatEpochToIso(scheduledAt)
        val response =
            "Sure! I've scheduled a reminder: \"$taskTitle\". " +
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

    private fun formatEpochToIso(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }
}
