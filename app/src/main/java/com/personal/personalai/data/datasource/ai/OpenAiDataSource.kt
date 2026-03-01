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
        private const val OPENAI_URL = "https://api.openai.com/v1/responses"
        private const val MODEL = "gpt-4o-mini"
        private const val MAX_HISTORY_MESSAGES = 20
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful personal AI assistant running on an Android app.
            You can answer questions and have natural conversations.
            You have access to real-time web search. When the user asks about current events,
            live data, or anything that benefits from searching the internet, use your web
            search capability to find up-to-date information.
            {{MEMORIES_SECTION}}
            IMPORTANT — Reminder scheduling: When the user wants a simple reminder or notification
            (e.g. "remind me to...", "set a reminder for...", "notify me when..."),
            respond helpfully AND append this tag at the very END of your response, on its own line:
            [TASK:{"title":"<short title>","description":"<brief description>","scheduledAt":"<ISO datetime like 2024-01-15T14:30:00>"}]

            IMPORTANT — AI Task scheduling: When the user wants to schedule the AI to automatically
            run a prompt at a future time and deliver the result
            (e.g. "summarize the news for me at 9am", "every morning give me a weather briefing",
            "schedule the AI to give me a motivational quote at 8pm",
            "have the AI check the news tomorrow at 7am"),
            respond helpfully AND append this tag at the very END of your response, on its own line:
            [TASK:{"title":"<short title>","description":"<brief description>","scheduledAt":"<ISO datetime>","taskType":"AI_PROMPT","aiPrompt":"<the exact prompt the AI should run>","outputTarget":"<NOTIFICATION|CHAT|BOTH>"}]
            Choose outputTarget based on context: NOTIFICATION for a push notification with the result,
            CHAT to add the result to the chat window, BOTH for both. Default to NOTIFICATION.
            For scheduledAt in both cases, infer the time from context. Default to 1 hour from now if unspecified.

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
            val inputMessages = JSONArray()
            chatHistory.takeLast(MAX_HISTORY_MESSAGES).forEach { msg ->
                inputMessages.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.content)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("instructions", buildSystemPrompt(memories))
                put("tools", JSONArray().put(JSONObject().put("type", "web_search_preview")))
                put("input", inputMessages)
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

            val output = JSONObject(responseBody).getJSONArray("output")
            for (i in 0 until output.length()) {
                val item = output.getJSONObject(i)
                if (item.getString("type") == "message") {
                    val content = item.getJSONArray("content")
                    for (j in 0 until content.length()) {
                        val contentItem = content.getJSONObject(j)
                        if (contentItem.getString("type") == "output_text") {
                            return@withContext Result.success(contentItem.getString("text").trim())
                        }
                    }
                }
            }
            return@withContext Result.failure(Exception("No text response in output"))
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
