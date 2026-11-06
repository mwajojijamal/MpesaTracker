package com.mpesatracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpesatracker.analytics.SpendingCalculator
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.BudgetEntity
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.FinancialClass
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType
import com.mpesatracker.data.repository.MpesaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BudgetProgressItem(
    val budget: BudgetEntity,
    val title: String,
    val periodLabel: String,
    val budgetLimitCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val progressRatio: Float,
    val isActivePeriod: Boolean
)

data class BudgetsUiState(
    val activeBudgets: List<BudgetProgressItem> = emptyList(),
    val historicalBudgets: List<BudgetProgressItem> = emptyList(),
    val categories: List<CategoryEntity> = emptyList()
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: MpesaRepository,
    private val notificationHelper: BudgetNotificationHelper
) : ViewModel() {

    val uiState: StateFlow<BudgetsUiState> = combine(
        repository.observeAllBudgets(),
        repository.observeAllTransactions(),
        repository.observeAllCategories()
    ) { budgets, transactions, categories ->
        val now = System.currentTimeMillis()
        val categoryMap = categories.associateBy { it.id }
        val completedOutflows = transactions.filter { tx ->
            if (tx.status != TransactionStatus.COMPLETED.name) return@filter false
            val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                .getOrDefault(TransactionType.OTHER)
            type.financialClass == FinancialClass.EXPENSE ||
                type.financialClass == FinancialClass.TRANSFER
        }

        val evaluated = budgets.map { budget ->
            val spent = calculateSpentForBudget(budget, completedOutflows)
            val remaining = budget.budgetAmount - spent
            val ratio = if (budget.budgetAmount > 0L) {
                spent.toFloat() / budget.budgetAmount.toFloat()
            } else 0f

            val title = if (budget.categoryId == null) {
                "Overall Monthly Budget"
            } else {
                categoryMap[budget.categoryId]?.name ?: "Category Budget"
            }

            val isActive = now in budget.startDate..budget.endDate
            if (isActive) {
                notificationHelper.checkAndNotifyBudgetThreshold(
                    budgetId = budget.id,
                    budgetTitle = title,
                    spentCents = spent,
                    limitCents = budget.budgetAmount
                )
            }

            BudgetProgressItem(
                budget = budget,
                title = title,
                periodLabel = formatPeriod(budget.startDate, budget.endDate),
                budgetLimitCents = budget.budgetAmount,
                spentCents = spent,
                remainingCents = remaining,
                progressRatio = ratio,
                isActivePeriod = isActive
            )
        }

        BudgetsUiState(
            activeBudgets = evaluated.filter { it.isActivePeriod },
            historicalBudgets = evaluated.filter { !it.isActivePeriod },
            categories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState()
    )

    fun saveMonthlyBudget(categoryId: Long?, amountKsh: String, monthOffset: Int = 0) {
        viewModelScope.launch {
            val amountCents = MoneyUtils.parseKshToCents(amountKsh)
            if (amountCents <= 0L) return@launch

            val now = System.currentTimeMillis()
            val startOfMonth = SpendingCalculator.getStartOfMonth(now, monthOffset)
            val endOfMonth = Calendar.getInstance().apply {
                timeInMillis = startOfMonth
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }.timeInMillis

            val entity = BudgetEntity(
                categoryId = categoryId,
                budgetAmount = amountCents,
                startDate = startOfMonth,
                endDate = endOfMonth
            )
            repository.insertBudget(entity)
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    private fun calculateSpentForBudget(
        budget: BudgetEntity,
        outflowTransactions: List<TransactionEntity>
    ): Long {
        return outflowTransactions.asSequence()
            .filter { it.transactionDate in budget.startDate..budget.endDate }
            .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
            .sumOf { it.amount }
    }

    private fun formatPeriod(startMillis: Long, endMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return "${sdf.format(Date(startMillis))} - ${sdf.format(Date(endMillis))}"
    }
}