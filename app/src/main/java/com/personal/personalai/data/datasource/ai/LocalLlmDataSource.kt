package com.personal.personalai.data.datasource.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.FunctionCall
import com.personal.personalai.localllm.api.LocalLlmMessage
import com.personal.personalai.localllm.api.LocalLlmResponse
import com.personal.personalai.localllm.api.LocalModel
import com.personal.personalai.localllm.download.ModelDownloadManager
import com.personal.personalai.localllm.engine.LiteRtLlmEngine
import com.personal.personalai.presentation.settings.PreferencesKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Bridges `:app`'s domain types to the `:localllm` module for on-device inference.
 *
 * Tool calling uses system-prompt injection + JSON parsing: tool schemas are injected
 * into the system message and the engine parses `{"tool_calls":[...]}` from model output.
 * When tool calls are detected, [AgentResponse.ToolCalls] is returned so that
 * [AgentLoopUseCase] executes the tools and loops — the same path as OpenAI/Ollama.
 */
class LocalLlmDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LiteRtLlmEngine,
    private val dataStore: DataStore<Preferences>,
    private val responsesApiClient: ResponsesApiClient
) {

    companion object {
        private val SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful personal AI assistant running directly on this Android device.
            You can answer questions and have natural conversations.

            You have access to powerful tools that let you take real actions for the user.
            Always use tools when the user's intent matches a tool capability. After using a
            tool, confirm the action in your final text response.

            CRITICAL — Asking questions: You must NEVER ask the user a question in your text
            response. If you need any information to complete a task (a time, a name, a preference,
            a confirmation), you MUST call the ask_user tool instead. Only use your text response
            for final answers and confirmations after all necessary information has been gathered.
            Asking a question in plain text is not allowed — always use ask_user.
            {{TOOLS_SECTION}}
            {{MEMORIES_SECTION}}
            Current date and time: {{DATETIME}}
        """.trimIndent()

        private val TOOLS_SECTION_TEMPLATE = """

            TOOLS
            When you need to call a tool, output ONLY the following JSON — no other text, no markdown fences, nothing else:
            {"tool_calls":[{"name":"<tool_name>","arguments":{"param1":"value1"}}]}

            To call multiple tools at once:
            {"tool_calls":[{"name":"tool1","arguments":{...}},{"name":"tool2","arguments":{...}}]}

            Available tools:
            [{{TOOL_SCHEMAS}}]

        """.trimIndent()
    }

    suspend fun sendMessageWithTools(
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): Result<AgentResponse> {
        if (!engine.isSupported()) {
            return Result.success(
                AgentResponse.Text(
                    "On-device AI requires Android 12 (API 31) or higher. " +
                    "Please use OpenAI or Ollama instead."
                )
            )
        }

        val prefs = dataStore.data.first()
        val modelId = prefs[PreferencesKeys.LOCAL_MODEL] ?: LocalModel.GEMMA_4_E2B.modelId
        val model = LocalModel.fromId(modelId) ?: LocalModel.GEMMA_4_E2B

        if (!ModelDownloadManager.isDownloaded(context, model)) {
            return Result.success(
                AgentResponse.Text(
                    "Model \"${model.displayName}\" is not downloaded yet. " +
                    "Please go to Settings → Local AI and tap Download."
                )
            )
        }

        return try {
            val messages = buildMessageList(conversationItems, memories, tools)
            val modelPath = ModelDownloadManager.modelFile(context, model).absolutePath
            val response  = engine.generate(messages, modelPath)

            Result.success(
                when (response) {
                    is LocalLlmResponse.Text -> AgentResponse.Text(response.text)
                    is LocalLlmResponse.ToolCalls -> AgentResponse.ToolCalls(
                        response.calls.map { call ->
                            FunctionCall(
                                id        = call.callId,
                                name      = call.name,
                                arguments = call.argumentsJson
                            )
                        }
                    )
                    is LocalLlmResponse.Error -> AgentResponse.Text(
                        "Local model error: ${response.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Conversation conversion ───────────────────────────────────────────────

    /**
     * Converts the Responses-API [conversationItems] JSONArray + [memories] + [tools] into
     * a flat [List<LocalLlmMessage>] that [LiteRtLlmEngine] understands.
     *
     * Tool schemas are injected into the system message so the model knows how to call them.
     *
     * Responses-API item shapes handled:
     *  - `{"role":"user","content":"..."}` → USER
     *  - `{"role":"assistant","content":"..."}` → ASSISTANT
     *  - `{"type":"function_call",...}` → skipped
     *  - `{"type":"function_call_output","output":"..."}` → ASSISTANT (tool result context)
     */
    private fun buildMessageList(
        conversationItems: JSONArray,
        memories: List<Memory>,
        tools: List<AgentTool>
    ): List<LocalLlmMessage> {
        val messages = mutableListOf<LocalLlmMessage>()

        messages.add(
            LocalLlmMessage(
                role    = LocalLlmMessage.Role.SYSTEM,
                content = buildSystemPrompt(memories, tools)
            )
        )

        for (i in 0 until conversationItems.length()) {
            val item = conversationItems.getJSONObject(i)
            when {
                item.has("role") -> {
                    val role    = item.getString("role")
                    val content = extractContent(item)
                    if (content.isNotBlank()) {
                        messages.add(
                            LocalLlmMessage(
                                role = if (role == "user") LocalLlmMessage.Role.USER
                                       else LocalLlmMessage.Role.ASSISTANT,
                                content = content
                            )
                        )
                    }
                }
                item.optString("type") == "function_call_output" -> {
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) {
                        messages.add(
                            LocalLlmMessage(
                                role    = LocalLlmMessage.Role.ASSISTANT,
                                content = "Tool result: $output"
                            )
                        )
                    }
                }
                // function_call items are skipped
            }
        }

        return messages
    }

    /**
     * Extracts text content from a conversation item.
     * Handles both `"content": "string"` and `"content": [{"type":"text","text":"..."}]`.
     */
    private fun extractContent(item: JSONObject): String {
        val raw = item.opt("content") ?: return ""
        return when (raw) {
            is String   -> raw
            is JSONArray -> buildString {
                for (i in 0 until raw.length()) {
                    val part = raw.optJSONObject(i) ?: continue
                    val text = part.optString("text").ifBlank { part.optString("content") }
                    if (text.isNotBlank()) append(text)
                }
            }
            else -> raw.toString()
        }
    }

    private fun buildSystemPrompt(memories: List<Memory>, tools: List<AgentTool>): String {
        val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        val toolsSection = if (tools.isNotEmpty()) {
            val schemas = tools.joinToString(",") { tool ->
                """{"name":"${esc(tool.name)}","description":"${esc(tool.description)}","parameters":${tool.parametersSchema()}}"""
            }
            TOOLS_SECTION_TEMPLATE.replace("{{TOOL_SCHEMAS}}", schemas)
        } else ""

        return SYSTEM_PROMPT_TEMPLATE
            .replace("{{TOOLS_SECTION}}", toolsSection)
            .replace("{{MEMORIES_SECTION}}", responsesApiClient.buildMemoriesSection(memories))
            .replace("{{DATETIME}}", now)
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
