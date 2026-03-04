package com.personal.personalai.domain.tools

/** Represents the outcome of one agent-loop turn from the AI backend. */
sealed class AgentResponse {
    data class Text(val text: String) : AgentResponse()
    data class ToolCalls(val calls: List<FunctionCall>) : AgentResponse()
}

data class FunctionCall(val id: String, val name: String, val arguments: String)
