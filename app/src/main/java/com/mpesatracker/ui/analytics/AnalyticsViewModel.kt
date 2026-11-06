package com.mpesatracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpesatracker.analytics.AnalyticsEngine
import com.mpesatracker.analytics.AnalyticsGranularity
import com.mpesatracker.analytics.AnalyticsReport
import com.mpesatracker.data.repository.MpesaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AnalyticsUiState(
    val granularity: AnalyticsGranularity = AnalyticsGranularity.MONTHLY,
    val report: AnalyticsReport = AnalyticsReport()
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    repository: MpesaRepository
) : ViewModel() {

    private val granularityState = MutableStateFlow(AnalyticsGranularity.MONTHLY)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.observeAllTransactions(),
        repository.observeAllCategories(),
        granularityState
    ) { transactions, categories, granularity ->
        val report = AnalyticsEngine.generateReport(
            allTransactions = transactions,
            categories = categories,
            granularity = granularity
        )
        AnalyticsUiState(
            granularity = granularity,
            report = report
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState()
    )

    fun setGranularity(granularity: AnalyticsGranularity) {
        granularityState.value = granularity
    }
}