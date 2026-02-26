package com.personal.personalai.domain.model

data class SendMessageResult(
    val message: Message,
    val createdTask: ScheduledTask?
)
