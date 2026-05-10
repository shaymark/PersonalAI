package com.personal.personalai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.personalai.domain.model.ApiUsageLog

@Entity(tableName = "api_usage_logs")
data class ApiUsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

fun ApiUsageLogEntity.toDomain() = ApiUsageLog(
    id = id,
    timestamp = timestamp,
    provider = provider,
    model = model,
    apiType = apiType,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cachedInputTokens = cachedInputTokens,
    totalTokens = totalTokens,
    audioDurationSeconds = audioDurationSeconds,
    estimatedCostUsd = estimatedCostUsd,
    latencyMs = latencyMs,
    success = success,
    errorMessage = errorMessage,
)

fun ApiUsageLog.toEntity() = ApiUsageLogEntity(
    id = id,
    timestamp = timestamp,
    provider = provider,
    model = model,
    apiType = apiType,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cachedInputTokens = cachedInputTokens,
    totalTokens = totalTokens,
    audioDurationSeconds = audioDurationSeconds,
    estimatedCostUsd = estimatedCostUsd,
    latencyMs = latencyMs,
    success = success,
    errorMessage = errorMessage,
)
