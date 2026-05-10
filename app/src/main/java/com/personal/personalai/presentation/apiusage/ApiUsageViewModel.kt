package com.personal.personalai.presentation.apiusage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.model.ApiUsageLog
import com.personal.personalai.domain.repository.ApiUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ApiUsageViewModel @Inject constructor(
    repository: ApiUsageRepository,
) : ViewModel() {

    val uiState: StateFlow<ApiUsageUiState> = repository.observeAllLogs()
        .map { logs -> buildState(logs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ApiUsageUiState(),
        )

    private fun buildState(logs: List<ApiUsageLog>): ApiUsageUiState {
        val zone = ZoneId.systemDefault()
        val now = java.time.LocalDate.now(zone)
        val weekStart = now.with(DayOfWeek.MONDAY)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = YearMonth.from(now).atDay(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        var weekUsd = 0.0
        var monthUsd = 0.0
        var allUsd = 0.0
        var weekTokens = 0
        var monthTokens = 0
        var allTokens = 0

        for (log in logs) {
            allUsd += log.estimatedCostUsd
            allTokens += log.totalTokens
            if (log.timestamp >= monthStart) {
                monthUsd += log.estimatedCostUsd
                monthTokens += log.totalTokens
            }
            if (log.timestamp >= weekStart) {
                weekUsd += log.estimatedCostUsd
                weekTokens += log.totalTokens
            }
        }

        return ApiUsageUiState(
            logs = logs,
            weeklyTotalUsd = weekUsd,
            monthlyTotalUsd = monthUsd,
            allTimeTotalUsd = allUsd,
            weeklyTokens = weekTokens,
            monthlyTokens = monthTokens,
            allTimeTokens = allTokens,
            isLoading = false,
        )
    }
}
