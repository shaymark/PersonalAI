package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Handles all communication with a locally-running Ollama server.
 *
 * Ollama supports the OpenAI Responses API format (`/v1/responses`) since v0.13.3,
 * so the request and response JSON structures are identical to [OpenAiDataSource].
 * The key differences are:
 *  - URL is user-configurable (passed in per call) rather than hardcoded
 *  - Model name is user-configurable (passed in per call)
 *  - No `Authorization` header is added (Ollama is unauthenticated by default)
 *  - `web_search_preview` is NOT included in the tools array (Ollama ignores it;
 *    web search is available via the [WebSearchTool] function tool instead)
 *
 * All HTTP execution and response parsing is delegated to [ResponsesApiClient].
 */
class OllamaDataSource @Inject constructor(
    private val apiClient: ResponsesApiClient
) {

    companion object {
        private const val RESPONSES_PATH = "/v1/responses"

        /**
         * Tools-aware system prompt for Ollama.
         * Identical in intent to OpenAiDataSource's TOOLS_SYSTEM_PROMPT_TEMPLATE but
         * omits the mention of built-in web search (web search is available via the
         * web_search function tool instead).
         */
        private val TOOLS_SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful personal AI assistant running on an Android app.
            You can answer questions and have natural conversations.
            You have access to real-time web search. When the user asks about current events,
            live data, or anything that benefits from searching the internet, use your web
            search capability to find up-to-date information.

            You have access to powerful tools that let you take real actions for the user

            Always use tools when the user's intent matches a tool capability. After using a tool,
            confirm the action in your final text response.

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
     * Agent-loop method. Sends accumulated [conversationItems] to the Ollama server at
     * [baseUrl] using the Responses API format and [model].
     *
     * @param baseUrl  Base URL of the Ollama server, e.g. `http://10.100.102.75:11434`.
     *                 A trailing slash is tolerated and stripped automatically.
     * @param model    Ollama model tag, e.g. `qwen3.5:4b`.
     * @param conversationItems  Accumulated conversation in Responses API item format.
     * @param memories User memories to inject into the system prompt.
     * @param tools    Available function tools (web_search_preview is never added for Ollama).
     */
    suspend fun sendMessageWithTools(
        baseUrl: String,
        model: String,
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse> = withContext(Dispatchers.IO) {
        try {
            // Build the tools array using only standard function tools.
            // web_search_preview is intentionally excluded (Ollama doesn't support that type).
            val toolsArray = JSONArray()
            tools.forEach { tool ->
                toolsArray.put(JSONObject().apply {
                    put("type", "function")
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parametersSchema())
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("instructions", buildSystemPrompt(memories))
                put("tools", toolsArray)
                put("input", conversationItems)
            }

            // Construct the full endpoint URL; apiKey = "" → no Authorization header
            val url = "${baseUrl.trimEnd('/')}$RESPONSES_PATH"
            apiClient.executeRequest(url, "", requestBody) { responseBody ->
                apiClient.parseAgentResponse(responseBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    private fun buildSystemPrompt(memories: List<Memory>): String {
        val now = apiClient.formatEpochToIso(System.currentTimeMillis())
        return TOOLS_SYSTEM_PROMPT_TEMPLATE
            .replace("{{MEMORIES_SECTION}}", apiClient.buildMemoriesSection(memories))
            .replace("{{DATETIME}}", now)
    }
}
