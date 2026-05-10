package com.personal.personalai.presentation.apiusage

import com.personal.personalai.domain.model.ApiUsageLog

data class ApiUsageUiState(
    val logs: List<ApiUsageLog> = emptyList(),
    val weeklyTotalUsd: Double = 0.0,
    val monthlyTotalUsd: Double = 0.0,
    val allTimeTotalUsd: Double = 0.0,
    val weeklyTokens: Int = 0,
    val monthlyTokens: Int = 0,
    val allTimeTokens: Int = 0,
    val isLoading: Boolean = true,
)
