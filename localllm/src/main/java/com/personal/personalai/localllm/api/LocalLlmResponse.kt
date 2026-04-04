package com.personal.personalai.localllm.api

/**
 * The result returned by [LiteRtLlmEngine.generate].
 *
 * Tool calling is handled via system-prompt injection + JSON output parsing.
 * When the model wants to invoke a tool it outputs a JSON object:
 *   {"tool_calls":[{"name":"...","arguments":{...}}]}
 * The engine parses that and returns [ToolCalls]. [LocalLlmDataSource] converts it to
 * [AgentResponse.ToolCalls] so that [AgentLoopUseCase] can execute the tools and loop.
 */
sealed class LocalLlmResponse {
    /** The model produced a complete text response. */
    data class Text(val text: String) : LocalLlmResponse()

    /** The model wants to invoke one or more tools before continuing. */
    data class ToolCalls(val calls: List<LocalToolCall>) : LocalLlmResponse()

    /** A non-recoverable inference error. */
    data class Error(val message: String) : LocalLlmResponse()
}

data class LocalToolCall(
    val callId: String,
    val name: String,
    val argumentsJson: String
)
