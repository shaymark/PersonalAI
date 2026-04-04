package com.personal.personalai.localllm.api

/**
 * A single message in a conversation passed to [LiteRtLlmEngine].
 *
 * `:localllm` uses this lightweight conversation type rather than the app's
 * Responses-API-shaped JSONArray. [LocalLlmDataSource] is responsible for translating
 * between the two.
 */
data class LocalLlmMessage(
    val role: Role,
    val content: String = "",
    val toolCalls: List<LocalLlmToolCallMessage> = emptyList(),
    val toolResponses: List<LocalLlmToolResponseMessage> = emptyList()
) {
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

data class LocalLlmToolCallMessage(
    val name: String,
    val argumentsJson: String
)

data class LocalLlmToolResponseMessage(
    val name: String,
    val responseJson: String
)
