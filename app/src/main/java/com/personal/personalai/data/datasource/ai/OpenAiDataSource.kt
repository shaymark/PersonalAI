package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import kotlinx.coroutines.Dispatchers
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

/**
 * Handles all communication with the OpenAI Chat Completions API.
 * Requires a valid API key and the user's stored memories on every call.
 * Contains no knowledge of DataStore, mock responses, or key management.
 */
class OpenAiDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
        private const val MAX_HISTORY_MESSAGES = 20
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful personal AI assistant running on an Android app.
            You can answer questions and have natural conversations.
            {{MEMORIES_SECTION}}
            IMPORTANT — Task scheduling: When the user wants to schedule a reminder or task
            (e.g. "remind me to...", "schedule a...", "set a reminder for...", "notify me..."),
            respond helpfully AND append this tag at the very END of your response, on its own line:
            [TASK:{"title":"<short task title>","description":"<brief description>","scheduledAt":"<ISO datetime like 2024-01-15T14:30:00>"}]
            For scheduledAt, infer the time from context. Default to 1 hour from now if unspecified.

            IMPORTANT — Memory: When the user asks you to remember something
            (e.g. "remember that my name is...", "remember I prefer...", "keep in mind..."),
            respond naturally AND append this tag at the very END of your response, on its own line:
            [MEMORY:{"content":"<what to remember>","topic":"<short category like name/preference/fact>"}]

            IMPORTANT — Forget by topic: When the user asks you to forget something specific
            (e.g. "forget my name", "forget my preferences"), respond naturally AND append:
            [FORGET:{"topic":"<the topic to delete>"}]

            IMPORTANT — Forget everything: When the user asks you to forget everything about them
            (e.g. "forget everything", "clear all my memories"), respond naturally AND append:
            [FORGET_ALL]

            Never include more than one of these tags per response. For all other messages, respond
            normally with no tags at all.
            Current date and time: {{DATETIME}}
        """.trimIndent()
    }

    /**
     * Sends [chatHistory] (which includes the current user message as the last entry)
     * to the OpenAI API using the provided [apiKey]. Injects [memories] into the system prompt.
     */
    suspend fun sendMessage(
        apiKey: String,
        chatHistory: List<Message>,
        memories: List<Memory>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = buildSystemPrompt(memories)
            val messages = JSONArray()

            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            chatHistory.takeLast(MAX_HISTORY_MESSAGES).forEach { msg ->
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

    private fun buildSystemPrompt(memories: List<Memory>): String {
        val memoriesSection = if (memories.isEmpty()) {
            ""
        } else buildString {
            append("\n\n--- User Memories ---\n")
            memories.forEach { memory ->
                if (memory.topic.isNotBlank()) append("[${memory.topic}] ")
                append(memory.content)
                append("\n")
            }
            append("--- End Memories ---")
        }
        val now = formatEpochToIso(System.currentTimeMillis())
        return SYSTEM_PROMPT_TEMPLATE
            .replace("{{MEMORIES_SECTION}}", memoriesSection)
            .replace("{{DATETIME}}", now)
    }

    private fun formatEpochToIso(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
