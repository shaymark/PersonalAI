package com.personal.personalai.localllm.api

/**
 * A single message in a conversation passed to [LiteRtLlmEngine].
 *
 * `:localllm` uses this simple flat type rather than the app's Responses API JSONArray.
 * [LocalLlmDataSource] in `:app` is responsible for converting between them.
 */
data class LocalLlmMessage(
    val role: Role,
    val content: String
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}
