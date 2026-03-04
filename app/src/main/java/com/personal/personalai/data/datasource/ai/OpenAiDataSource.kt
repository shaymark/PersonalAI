package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.FunctionCall
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
 * Handles all communication with the OpenAI Responses API.
 * Requires a valid API key and the user's stored memories on every call.
 * Contains no knowledge of DataStore, mock responses, or key management.
 */
class OpenAiDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/responses"
        private const val MODEL = "gpt-4o"
        private const val MAX_HISTORY_MESSAGES = 20
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

        /** Used by the legacy sendMessage() path (tag-based, for backward compat). */
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
            For recurring reminders (e.g. "every day", "daily", "every morning", "every week", "every Monday"),
            add "recurrenceType":"DAILY" or "recurrenceType":"WEEKLY" to the tag.
            scheduledAt should be the FIRST occurrence. Use DAILY for daily/every day/morning/evening/night keywords,
            WEEKLY for weekly/every week/every Monday/etc. keywords.

            IMPORTANT — AI Task scheduling: When the user wants to schedule the AI to automatically
            run a prompt at a future time and deliver the result
            (e.g. "summarize the news for me at 9am", "every morning give me a weather briefing",
            "schedule the AI to give me a motivational quote at 8pm",
            "have the AI check the news tomorrow at 7am"),
            respond helpfully AND append this tag at the very END of your response, on its own line:
            [TASK:{"title":"<short title>","description":"<brief description>","scheduledAt":"<ISO datetime>","taskType":"AI_PROMPT","aiPrompt":"<the exact prompt the AI should run>","outputTarget":"<NOTIFICATION|CHAT|BOTH>"}]
            Choose outputTarget based on context: NOTIFICATION for a push notification with the result,
            CHAT to add the result to the chat window, BOTH for both. Default to NOTIFICATION.
            For recurring AI tasks, also add "recurrenceType":"DAILY" or "recurrenceType":"WEEKLY".
            scheduledAt should be the FIRST occurrence.
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

        /** Used by sendMessageWithTools() — instructs the LLM to use function tools instead of tags. */
        private val TOOLS_SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful personal AI assistant running on an Android app.
            You can answer questions and have natural conversations.
            You have access to real-time web search. When the user asks about current events,
            live data, or anything that benefits from searching the internet, use your web
            search capability to find up-to-date information.

            You have access to powerful tools that let you take real actions for the user:
            - schedule_task: Create reminders and schedule AI tasks at specific times
            - save_memory: Persist information about the user across conversations
            - forget_memory / forget_all_memories: Remove stored memories when asked
            - open_app: Launch any installed app on the device
            - get_installed_apps: Discover which apps are installed
            - read_contacts: Search the user's contact list for names and phone numbers
            - get_clipboard: Read the current clipboard contents
            - ask_user: Ask the user a question and wait for their answer before continuing

            Always use tools when the user's intent matches a tool capability. After using a tool,
            confirm the action in your final text response. For scheduled_at in schedule_task,
            default to 1 hour from now if no time is specified.

            CRITICAL — Asking questions: You must NEVER ask the user a question in your text
            response. If you need any information to complete a task (a time, a name, a preference,
            a confirmation), you MUST call the ask_user tool instead. Only use your text response
            for final answers and confirmations after all necessary information has been gathered.
            Asking a question in plain text is not allowed — always use ask_user.
            {{MEMORIES_SECTION}}
            Current date and time: {{DATETIME}}
        """.trimIndent()
    }

    /**
     * Legacy single-turn method. Sends [chatHistory] to the Responses API.
     * Used by [AiRepositoryImpl.sendMessage] (e.g. TaskReminderWorker old path).
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

            executeRequest(apiKey, requestBody) { responseBody ->
                parseTextFromOutput(responseBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Agent-loop method. Sends accumulated [conversationItems] (Responses API format) and
     * includes function tool definitions. Returns either a text response or function calls.
     */
    suspend fun sendMessageWithTools(
        apiKey: String,
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse> = withContext(Dispatchers.IO) {
        try {
            val toolsArray = JSONArray()
            toolsArray.put(JSONObject().put("type", "web_search_preview"))
            tools.forEach { tool ->
                toolsArray.put(JSONObject().apply {
                    put("type", "function")
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parametersSchema())
                })
            }

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("instructions", buildToolsSystemPrompt(memories))
                put("tools", toolsArray)
                put("input", conversationItems)
            }

            executeRequest(apiKey, requestBody) { responseBody ->
                parseAgentResponse(responseBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private fun parseTextFromOutput(responseBody: String): Result<String> {
        val output = JSONObject(responseBody).getJSONArray("output")
        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            if (item.getString("type") == "message") {
                val content = item.getJSONArray("content")
                for (j in 0 until content.length()) {
                    val contentItem = content.getJSONObject(j)
                    if (contentItem.getString("type") == "output_text") {
                        return Result.success(contentItem.getString("text").trim())
                    }
                }
            }
        }
        return Result.failure(Exception("No text response in output"))
    }

    private fun parseAgentResponse(responseBody: String): Result<AgentResponse> {
        val output = JSONObject(responseBody).getJSONArray("output")
        val functionCalls = mutableListOf<FunctionCall>()

        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            when (item.optString("type")) {
                "function_call" -> {
                    functionCalls.add(
                        FunctionCall(
                            id = item.getString("call_id"),
                            name = item.getString("name"),
                            arguments = item.getString("arguments")
                        )
                    )
                }
                "message" -> {
                    if (functionCalls.isEmpty()) {
                        val content = item.getJSONArray("content")
                        for (j in 0 until content.length()) {
                            val contentItem = content.getJSONObject(j)
                            if (contentItem.optString("type") == "output_text") {
                                return Result.success(
                                    AgentResponse.Text(contentItem.getString("text").trim())
                                )
                            }
                        }
                    }
                }
            }
        }

        return if (functionCalls.isNotEmpty()) {
            Result.success(AgentResponse.ToolCalls(functionCalls))
        } else {
            Result.failure(Exception("No text or function calls in response"))
        }
    }

    // ── HTTP execution ────────────────────────────────────────────────────────

    private fun <T> executeRequest(
        apiKey: String,
        requestBody: JSONObject,
        parseResponse: (String) -> Result<T>
    ): Result<T> {
        val request = Request.Builder()
            .url(OPENAI_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBodyStr = response.body?.string()
            ?: return Result.failure(Exception("Empty response from OpenAI"))

        if (!response.isSuccessful) {
            val errorMsg = runCatching {
                JSONObject(responseBodyStr).getJSONObject("error").getString("message")
            }.getOrDefault("OpenAI error: HTTP ${response.code}")
            return Result.failure(Exception(errorMsg))
        }

        return parseResponse(responseBodyStr)
    }

    // ── System prompts ────────────────────────────────────────────────────────

    private fun buildSystemPrompt(memories: List<Memory>): String {
        val now = formatEpochToIso(System.currentTimeMillis())
        return SYSTEM_PROMPT_TEMPLATE
            .replace("{{MEMORIES_SECTION}}", buildMemoriesSection(memories))
            .replace("{{DATETIME}}", now)
    }

    private fun buildToolsSystemPrompt(memories: List<Memory>): String {
        val now = formatEpochToIso(System.currentTimeMillis())
        return TOOLS_SYSTEM_PROMPT_TEMPLATE
            .replace("{{MEMORIES_SECTION}}", buildMemoriesSection(memories))
            .replace("{{DATETIME}}", now)
    }

    private fun buildMemoriesSection(memories: List<Memory>): String =
        if (memories.isEmpty()) {
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

    private fun formatEpochToIso(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
