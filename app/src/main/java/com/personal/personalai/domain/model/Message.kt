package com.personal.personalai.domain.model

data class Message(
    val id: Long = 0,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val hasCreatedTask: Boolean = false,
    val createdTaskTitle: String? = null
)

enum class MessageRole {
    USER, ASSISTANT
}
