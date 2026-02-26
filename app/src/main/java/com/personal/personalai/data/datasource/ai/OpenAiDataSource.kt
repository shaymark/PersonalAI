package com.personal.personalai.data.datasource.ai

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
 * Requires a valid API key to be passed on every call.
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

    /**
     * Sends [chatHistory] (which includes the current user message as the last entry)
     * to the OpenAI API using the provided [apiKey].
     */
    suspend fun sendMessage(apiKey: String, chatHistory: List<Message>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val now = formatEpochToIso(System.currentTimeMillis())
                val systemPrompt = SYSTEM_PROMPT.replace("{{DATETIME}}", now)

                val messages = JSONArray()

                // System prompt
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // Chat history — already includes current user message at the end.
                // Limit to avoid exceeding context windows on long conversations.
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

    private fun formatEpochToIso(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
