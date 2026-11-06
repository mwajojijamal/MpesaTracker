package com.mpesatracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpesatracker.analytics.DashboardSummary
import com.mpesatracker.analytics.SpendingCalculator
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.repository.MpesaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class DashboardPeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    ALL_TIME("All Time")
}

data class DashboardUiState(
    val selectedPeriod: DashboardPeriod = DashboardPeriod.THIS_MONTH,
    val summary: DashboardSummary = DashboardSummary(),
    val categoryMap: Map<Long, CategoryEntity> = emptyMap()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: MpesaRepository
) : ViewModel() {

    private val selectedPeriodState = MutableStateFlow(DashboardPeriod.THIS_MONTH)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeAllTransactions(),
        repository.observeAllCategories(),
        selectedPeriodState
    ) { allTransactions, categories, period ->
        val now = System.currentTimeMillis()
        val cutoff = when (period) {
            DashboardPeriod.THIS_MONTH -> SpendingCalculator.getStartOfMonth(now, 0)
            DashboardPeriod.LAST_30_DAYS -> {
                Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -30)
                }.timeInMillis
            }
            DashboardPeriod.ALL_TIME -> 0L
        }

        val periodTransactions = allTransactions.filter { it.transactionDate >= cutoff }
        val summary = SpendingCalculator.calculateDashboardSummary(
            allTransactions = allTransactions,
            filteredTransactions = periodTransactions,
            categories = categories,
            nowMillis = now
        )

        DashboardUiState(
            selectedPeriod = period,
            summary = summary,
            categoryMap = categories.associateBy { it.id }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun selectPeriod(period: DashboardPeriod) {
        selectedPeriodState.value = period
    }
}