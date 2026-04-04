package com.personal.personalai.localllm.engine

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.personal.personalai.localllm.api.LocalLlmMessage
import com.personal.personalai.localllm.api.LocalLlmResponse
import com.personal.personalai.localllm.api.LocalToolCall
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * On-device LLM inference engine backed by [com.google.ai.edge.litertlm].
 *
 * - GPU backend is preferred; falls back to CPU if unavailable.
 * - Requires Android 12 (API 31); [isSupported] must be checked before calling [generate].
 * - The [Engine] is created lazily and cached by model path.
 * - **Tool calling** uses system-prompt injection + JSON output parsing. LiteRT-LM's native
 *   `automaticToolCalling` is disabled because its parser cannot handle Gemma 4's
 *   `<|tool_call>` output format. Instead, [LocalLlmDataSource] injects tool schemas into
 *   the system message and [generate] parses `{"tool_calls":[...]}` from the model output,
 *   returning [LocalLlmResponse.ToolCalls] when a tool call is detected.
 */
class LiteRtLlmEngine(private val context: Context) {

    companion object {
        /** Minimum Android API level required by LiteRT-LM GPU backend. */
        const val MIN_API_LEVEL = Build.VERSION_CODES.S  // API 31 = Android 12

        private const val TOP_K       = 64
        private const val TOP_P       = 0.95
        private const val TEMPERATURE = 1.0
        // 8192 gives ~4× more context than the default 4096 while staying within Gemma 4's
        // supported range (up to 32K). Increase further only if your device has enough RAM.
        private const val MAX_TOKENS  = 16256
    }

    private var currentModelPath: String? = null
    private var engine: Engine? = null
    private var currentConversation: com.google.ai.edge.litertlm.Conversation? = null

    /** Returns true if this device meets the minimum OS requirement for LiteRT-LM. */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_API_LEVEL

    /**
     * Generates a response for the [messages] conversation.
     *
     * Tool schemas are expected to be injected into the system message by the caller
     * ([LocalLlmDataSource]). When the model outputs a JSON tool call, [generate] returns
     * [LocalLlmResponse.ToolCalls]; otherwise [LocalLlmResponse.Text].
     *
     * Tokens are streamed to [onToken] as they arrive.
     *
     * @param messages Full conversation: first message is SYSTEM (prompt + tool schemas +
     *   memories), then alternating USER/ASSISTANT history, last message is the current USER turn.
     * @param modelPath Absolute path to the downloaded `.litertlm` model file.
     * @param onToken Optional per-token streaming callback.
     */
    suspend fun generate(
        messages: List<LocalLlmMessage>,
        modelPath: String,
        onToken: (String) -> Unit = {}
    ): LocalLlmResponse {
        if (!isSupported()) {
            return LocalLlmResponse.Error(
                "On-device AI requires Android 12 (API 31) or higher. " +
                "Your device is running API ${Build.VERSION.SDK_INT}."
            )
        }
        return try {
            ensureEngine(modelPath)
            val eng = engine ?: return LocalLlmResponse.Error("Failed to load model.")

            val systemContents  = buildSystemContents(messages)
            val historyMessages = buildHistoryMessages(messages)
            val userText        = extractLatestUserMessage(messages)

            // LiteRT-LM only supports one active session at a time — close the previous
            // conversation before creating a new one.
            closeCurrentConversation()

            val config = ConversationConfig(
                systemInstruction    = systemContents,
                initialMessages      = historyMessages,
                tools                = emptyList(),
                samplerConfig        = SamplerConfig(topK = TOP_K, topP = TOP_P, temperature = TEMPERATURE),
                automaticToolCalling = false
            )

            val conversation = eng.createConversation(config)
            currentConversation = conversation

            // Flow-based streaming: collect all Message tokens, assemble the final text
            val assembled = StringBuilder()
            conversation.sendMessageAsync(userText)
                .toList()
                .forEach { msg ->
                    val text = msg.contents?.let { c ->
                        c.contents.filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                    }.orEmpty()
                    onToken(text)
                    assembled.append(text)
                }

            val fullText = assembled.toString().trim()
            val toolCalls = parseToolCalls(fullText)
            if (toolCalls != null) {
                LocalLlmResponse.ToolCalls(toolCalls)
            } else {
                LocalLlmResponse.Text(fullText)
            }
        } catch (e: Exception) {
            LocalLlmResponse.Error(e.message ?: "Unknown inference error")
        }
    }

    /** Releases the current conversation session without destroying the engine. */
    @Synchronized
    private fun closeCurrentConversation() {
        currentConversation?.close()
        currentConversation = null
    }

    /** Releases the conversation and the native engine. Call when switching away from Local. */
    @Synchronized
    fun close() {
        closeCurrentConversation()
        engine?.close()
        engine = null
        currentModelPath = null
    }

    // ── Engine session management ─────────────────────────────────────────────

    @Synchronized
    private fun ensureEngine(modelPath: String) {
        if (currentModelPath == modelPath && engine != null) return
        closeCurrentConversation()
        engine?.close()
        engine = null
        currentModelPath = null

        val cacheDir = context.cacheDir.absolutePath

        val newEngine = try {
            Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU(), maxNumTokens = MAX_TOKENS, cacheDir = cacheDir))
        } catch (_: Exception) {
            // GPU not available — fall back to CPU
            Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = MAX_TOKENS, cacheDir = cacheDir))
        }
        newEngine.initialize()
        engine = newEngine
        currentModelPath = modelPath
    }

    // ── Tool call parsing ─────────────────────────────────────────────────────

    /**
     * Detects and parses tool calls from model output.
     *
     * Three strategies are tried in order:
     * 1. **Strict JSON** — `{"tool_calls":[{"name":"...","arguments":{...}}]}`
     * 2. **Gemma 4 native** — `<|tool_call>call:name{args}<tool_call|>`
     * 3. **Regex fallback** — handles malformed JSON like `"name":"send":"send_sms"`
     *    where the model almost followed our format but produced invalid JSON.
     *
     * Returns null when no tool call is detected (plain text response).
     */
    private fun parseToolCalls(text: String): List<LocalToolCall>? {
        if (text.contains("\"tool_calls\"")) {
            parseJsonToolCalls(text)?.let { return it }
        }
        if (text.contains("<|tool_call>")) {
            parseGemmaToolCalls(text)?.let { return it }
        }
        if (text.contains("\"name\"") && text.contains("\"arguments\"")) {
            parseRegexToolCalls(text)?.let { return it }
        }
        return null
    }

    private fun parseJsonToolCalls(text: String): List<LocalToolCall>? {
        return try {
            val cleaned = text.replace(Regex("```[a-z]*\\s*"), "").replace("```", "").trim()
            val jsonStr = extractBalancedBraces(cleaned, cleaned.indexOf('{')) ?: return null
            val root = JSONObject(jsonStr)
            val callsArr: JSONArray = root.optJSONArray("tool_calls") ?: return null
            (0 until callsArr.length()).mapNotNull { i ->
                val call = callsArr.optJSONObject(i) ?: return@mapNotNull null
                val name = call.optString("name").ifBlank { return@mapNotNull null }
                val argsJson = when (val args = call.opt("arguments")) {
                    is JSONObject -> args.toString()
                    is String     -> args
                    else          -> "{}"
                }
                LocalToolCall(callId = UUID.randomUUID().toString(), name = name, argumentsJson = argsJson)
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    /**
     * Regex-based fallback for malformed JSON the model sometimes produces.
     *
     * Handles cases like `"name":"send":"send_sms"` (doubled value — takes the last
     * quoted string) and text mixed around the JSON fragment.
     */
    private fun parseRegexToolCalls(text: String): List<LocalToolCall>? {
        // Matches "name" : (optional extra "X":)* "actual_name"
        val nameRegex = Regex(""""name"\s*:\s*(?:"[^"]*"\s*:\s*)*"([^"]+)"""")
        val calls = mutableListOf<LocalToolCall>()

        for (nameMatch in nameRegex.findAll(text)) {
            val toolName = nameMatch.groupValues[1]
            val afterName = nameMatch.range.last

            // Find "arguments" after this name match
            val argsKeyIdx = text.indexOf("\"arguments\"", afterName)
            if (argsKeyIdx == -1) continue

            // Find the opening '{' of the arguments object
            val colonAfterArgs = text.indexOf(':', argsKeyIdx + "\"arguments\"".length)
            if (colonAfterArgs == -1) continue
            val braceIdx = text.indexOf('{', colonAfterArgs)
            if (braceIdx == -1) continue

            val argsBlock = extractBalancedBraces(text, braceIdx) ?: continue
            val argsJson = try { JSONObject(argsBlock); argsBlock } catch (_: Exception) { "{}" }

            calls.add(LocalToolCall(
                callId = UUID.randomUUID().toString(),
                name = toolName,
                argumentsJson = argsJson
            ))
        }

        return calls.takeIf { it.isNotEmpty() }
    }

    /**
     * Parses Gemma 4's native `<|tool_call>call:name{args}<tool_call|>` format.
     *
     * The args block typically has an unquoted outer wrapper around the real JSON args, e.g.:
     *   `{phone_number:"05553552473",message:"hi"}` — almost-JSON, unquoted keys
     *   `{message:{"phone_number":"...", "message":"..."}}` — inner JSON is the real args
     *
     * Strategy: try the block as JSON; if that fails, find the first `{"` sub-object.
     */
    private fun parseGemmaToolCalls(text: String): List<LocalToolCall>? {
        val calls = mutableListOf<LocalToolCall>()
        val prefix = "<|tool_call>call:"
        var searchFrom = 0

        while (true) {
            val start = text.indexOf(prefix, searchFrom)
            if (start == -1) break

            val nameStart = start + prefix.length
            val braceStart = text.indexOf('{', nameStart)
            if (braceStart == -1) break

            val toolName = text.substring(nameStart, braceStart).trim()
            if (toolName.isBlank()) { searchFrom = braceStart + 1; continue }

            val argsBlock = extractBalancedBraces(text, braceStart)
            if (argsBlock == null) { searchFrom = braceStart + 1; continue }

            val argsJson = extractJsonArgsFromGemmaBlock(argsBlock)
            calls.add(LocalToolCall(callId = UUID.randomUUID().toString(), name = toolName, argumentsJson = argsJson))
            searchFrom = braceStart + argsBlock.length
        }

        return calls.takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts usable JSON arguments from a Gemma args block like
     * `{message:{"phone_number":"...","message":"..."}}`.
     *
     * Tries in order:
     * 1. The whole block as JSON (e.g., already `{"key":"value"}`)
     * 2. The first inner `{"` sub-object (real args wrapped in an outer unquoted key)
     * 3. Empty args `{}` as a last resort
     */
    private fun extractJsonArgsFromGemmaBlock(argsBlock: String): String {
        // 1. Try as-is
        try { JSONObject(argsBlock); return argsBlock } catch (_: Exception) {}

        // 2. Find first properly-quoted JSON sub-object: look for {"
        var searchAt = 0
        while (true) {
            val quotedStart = argsBlock.indexOf("{\"", searchAt)
            if (quotedStart == -1) break
            val inner = extractBalancedBraces(argsBlock, quotedStart) ?: break
            try { JSONObject(inner); return inner } catch (_: Exception) {}
            searchAt = quotedStart + 1
        }

        return "{}"
    }

    /**
     * Extracts the balanced `{...}` block starting at [start] in [text].
     * Returns null if braces are unbalanced.
     */
    private fun extractBalancedBraces(text: String, start: Int): String? {
        if (start < 0 || start >= text.length) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    // ── Prompt helpers ────────────────────────────────────────────────────────

    /**
     * Builds a [Contents] object for the system instruction from the SYSTEM message
     * in [messages] (which contains the tools system prompt + tool schemas + memories).
     */
    private fun buildSystemContents(messages: List<LocalLlmMessage>): Contents {
        val systemText = messages.firstOrNull { it.role == LocalLlmMessage.Role.SYSTEM }?.content
            ?: return Contents.of("")
        return Contents.of(systemText)
    }

    /**
     * Converts prior USER/ASSISTANT turns (all but the final user message) into
     * [Message] objects for [ConversationConfig.initialMessages].
     *
     * History is capped at the last 20 messages to fit within the context window.
     */
    private fun buildHistoryMessages(messages: List<LocalLlmMessage>): List<Message> {
        val nonSystem = messages.filter { it.role != LocalLlmMessage.Role.SYSTEM }
        // Drop the final user message — it is sent live via sendMessageAsync
        val history = if (nonSystem.lastOrNull()?.role == LocalLlmMessage.Role.USER) {
            nonSystem.dropLast(1)
        } else nonSystem

        return history.takeLast(20).map { msg ->
            when (msg.role) {
                LocalLlmMessage.Role.USER      -> Message.user(msg.content)
                LocalLlmMessage.Role.ASSISTANT -> Message.model(Contents.of(msg.content))
                LocalLlmMessage.Role.SYSTEM    -> Message.system(msg.content)
            }
        }
    }

    private fun extractLatestUserMessage(messages: List<LocalLlmMessage>): String =
        messages.lastOrNull { it.role == LocalLlmMessage.Role.USER }?.content
            ?: error("No user message in conversation")
}
