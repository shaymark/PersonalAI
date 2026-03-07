package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.FunctionCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared HTTP client and Responses-API response parser.
 *
 * Encapsulates everything that is identical between [OpenAiDataSource] (cloud) and
 * [OllamaDataSource] (local LAN server):
 *  - HTTP POST execution with conditional `Authorization` header
 *  - Parsing `output[]` → [AgentResponse] (text or tool calls)
 *  - Parsing `output[]` → plain text string
 *  - Formatting user memories into the prompt block
 *  - ISO-8601 date/time formatting
 *
 * All higher-level concerns (URL, model, system-prompt template, web_search_preview inclusion)
 * live in the individual data sources that inject this class.
 */
@Singleton
class ResponsesApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }

    // ── HTTP execution ────────────────────────────────────────────────────────

    /**
     * Executes a POST to [url] with [requestBody] and delegates response parsing to
     * [parseResponse].
     *
     * The `Authorization: Bearer` header is added only when [apiKey] is non-blank,
     * so Ollama (which uses no auth) can pass an empty string.
     *
     * Error messages use "Responses API" as the source label so they read correctly
     * regardless of which provider is calling this method.
     */
    fun <T> executeRequest(
        url: String,
        apiKey: String,
        requestBody: JSONObject,
        parseResponse: (String) -> Result<T>
    ): Result<T> {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(MEDIA_TYPE_JSON))
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        val request = requestBuilder.build()

        val response = okHttpClient.newCall(request).execute()
        val responseBodyStr = response.body?.string()
            ?: return Result.failure(Exception("Empty response from Responses API"))

        if (!response.isSuccessful) {
            val errorMsg = runCatching {
                JSONObject(responseBodyStr).getJSONObject("error").getString("message")
            }.getOrDefault("Responses API error: HTTP ${response.code}")
            return Result.failure(Exception(errorMsg))
        }

        return parseResponse(responseBodyStr)
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * Parses a Responses API `output` array and returns either [AgentResponse.Text]
     * (for a plain text reply) or [AgentResponse.ToolCalls] (for function-call requests).
     *
     * Function-call items are collected first; if any are found the text items are ignored
     * and [AgentResponse.ToolCalls] is returned. Otherwise the first `output_text` content
     * item is returned as [AgentResponse.Text].
     */
    fun parseAgentResponse(responseBody: String): Result<AgentResponse> {
        val output = JSONObject(responseBody).getJSONArray("output")
        val functionCalls = mutableListOf<FunctionCall>()

        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            when (item.optString("type")) {
                "function_call" -> {
                    functionCalls.add(
                        FunctionCall(
                            id        = item.getString("call_id"),
                            name      = item.getString("name"),
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

    /**
     * Parses a Responses API `output` array and returns the first `output_text` string.
     * Used by the legacy single-turn [OpenAiDataSource.sendMessage] path.
     */
    fun parseTextFromOutput(responseBody: String): Result<String> {
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

    // ── Shared prompt helpers ─────────────────────────────────────────────────

    /**
     * Formats [memories] into a `--- User Memories ---` block suitable for injection
     * into any system prompt template via `{{MEMORIES_SECTION}}`.
     * Returns an empty string when the list is empty.
     */
    fun buildMemoriesSection(memories: List<Memory>): String =
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

    /**
     * Formats an epoch-millis timestamp to `yyyy-MM-dd'T'HH:mm:ss` in the device's
     * local time zone.
     */
    fun formatEpochToIso(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
