package com.personal.personalai.domain.model

data class ApiUsageLog(
    val id: Long = 0,
    val timestamp: Long,
    val provider: String,
    val model: String,
    val apiType: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedInputTokens: Int = 0,
    val totalTokens: Int = 0,
    val audioDurationSeconds: Double? = null,
    val estimatedCostUsd: Double = 0.0,
    val latencyMs: Long = 0,
    val success: Boolean = true,
    val errorMessage: String? = null,
)
