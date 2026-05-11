package com.personal.personalai.domain.tools

/**
 * Represents the outcome of one agent-loop turn from the AI backend.
 *
 * [responseId] is the OpenAI Responses-API response identifier, used to chain
 * subsequent calls via `previous_response_id`. Null for backends that don't
 * support stateful chaining (Ollama, on-device LiteRT) — callers must
 * fall back to stateless mode in that case.
 */
sealed class AgentResponse {
    abstract val responseId: String?

    data class Text(
        val text: String,
        override val responseId: String? = null,
    ) : AgentResponse()

    data class ToolCalls(
        val calls: List<FunctionCall>,
        override val responseId: String? = null,
    ) : AgentResponse()
}

data class FunctionCall(val id: String, val name: String, val arguments: String)
