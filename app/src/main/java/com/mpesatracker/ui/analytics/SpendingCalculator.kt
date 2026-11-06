package com.mpesatracker.analytics

import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.FinancialClass
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CategorySpendingItem(
    val categoryId: Long?,
    val categoryName: String,
    val totalCents: Long,
    val transactionCount: Int,
    val percentageOfTotal: Float
)

data class MonthlyTrendPoint(
    val monthLabel: String,
    val yearMonthKey: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val chargesCents: Long
)

data class DashboardSummary(
    val totalReceivedCents: Long = 0L,
    val totalSpentCents: Long = 0L,
    val totalTransferredCents: Long = 0L,
    val totalWithdrawalsCents: Long = 0L,
    val totalChargesCents: Long = 0L,
    val todaySpentCents: Long = 0L,
    val thisWeekSpentCents: Long = 0L,
    val currentMonthSpentCents: Long = 0L,
    val previousMonthSpentCents: Long = 0L,
    val currentMonthReceivedCents: Long = 0L,
    val categoryBreakdown: List<CategorySpendingItem> = emptyList(),
    val monthlyTrends: List<MonthlyTrendPoint> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList()
)

/**
 * Pure, deterministic financial calculation engine operating strictly on minor units (Long cents).
 * Properly separates Income, Consumption Expenses, Transfers, Withdrawals, and Fees,
 * and ignores REVERSED or FAILED transactions so nothing is double-counted.
 */
object SpendingCalculator {

    fun calculateDashboardSummary(
        allTransactions: List<TransactionEntity>,
        filteredTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): DashboardSummary {
        val activeFiltered = filteredTransactions.filter {
            it.status == TransactionStatus.COMPLETED.name
        }
        val activeAll = allTransactions.filter {
            it.status == TransactionStatus.COMPLETED.name
        }

        val categoryMap = categories.associateBy { it.id }

        var totalReceived = 0L
        var totalSpent = 0L
        var totalTransferred = 0L
        var totalWithdrawals = 0L
        var totalCharges = 0L

        for (tx in activeFiltered) {
            val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                .getOrDefault(TransactionType.OTHER)

            // Fees are tracked in their own bucket and never added twice into amount
            totalCharges += tx.charges

            when (type.financialClass) {
                FinancialClass.INCOME -> totalReceived += tx.amount
                FinancialClass.EXPENSE -> totalSpent += tx.amount
                FinancialClass.TRANSFER -> totalTransferred += tx.amount
                FinancialClass.WITHDRAWAL -> totalWithdrawals += tx.amount
                FinancialClass.FEES -> totalCharges += tx.amount
                FinancialClass.REVERSAL -> Unit
            }
        }

        // Time boundaries for Today, This Week, This Month, and Previous Month
        val startOfToday = getStartOfDay(nowMillis)
        val startOfWeek = getStartOfWeek(nowMillis)
        val startOfCurrentMonth = getStartOfMonth(nowMillis, monthOffset = 0)
        val startOfPreviousMonth = getStartOfMonth(nowMillis, monthOffset = -1)

        var todaySpent = 0L
        var weekSpent = 0L
        var currentMonthSpent = 0L
        var previousMonthSpent = 0L
        var currentMonthReceived = 0L

        for (tx in activeAll) {
            val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                .getOrDefault(TransactionType.OTHER)

            val isOutflow = type.financialClass == FinancialClass.EXPENSE ||
                type.financialClass == FinancialClass.TRANSFER

            if (isOutflow) {
                if (tx.transactionDate >= startOfToday) {
                    todaySpent += tx.amount
                }
                if (tx.transactionDate >= startOfWeek) {
                    weekSpent += tx.amount
                }
                if (tx.transactionDate >= startOfCurrentMonth) {
                    currentMonthSpent += tx.amount
                } else if (tx.transactionDate in startOfPreviousMonth until startOfCurrentMonth) {
                    previousMonthSpent += tx.amount
                }
            } else if (type.financialClass == FinancialClass.INCOME) {
                if (tx.transactionDate >= startOfCurrentMonth) {
                    currentMonthReceived += tx.amount
                }
            }
        }

        // Category breakdown (for Expenses & Transfers in the selected period)
        val outflowTxs = activeFiltered.filter { tx ->
            val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                .getOrDefault(TransactionType.OTHER)
            type.financialClass == FinancialClass.EXPENSE ||
                type.financialClass == FinancialClass.TRANSFER
        }
        val totalOutflowForCategories = outflowTxs.sumOf { it.amount }.coerceAtLeast(1L)

        val categoryBreakdown = outflowTxs
            .groupBy { it.categoryId }
            .map { (catId, txs) ->
                val sum = txs.sumOf { it.amount }
                val catName = catId?.let { categoryMap[it]?.name } ?: "Uncategorised"
                CategorySpendingItem(
                    categoryId = catId,
                    categoryName = catName,
                    totalCents = sum,
                    transactionCount = txs.size,
                    percentageOfTotal = (sum.toFloat() / totalOutflowForCategories.toFloat())
                )
            }
            .sortedByDescending { it.totalCents }

        val monthlyTrends = calculateLastSixMonthsTrend(activeAll, nowMillis)

        return DashboardSummary(
            totalReceivedCents = totalReceived,
            totalSpentCents = totalSpent,
            totalTransferredCents = totalTransferred,
            totalWithdrawalsCents = totalWithdrawals,
            totalChargesCents = totalCharges,
            todaySpentCents = todaySpent,
            thisWeekSpentCents = weekSpent,
            currentMonthSpentCents = currentMonthSpent,
            previousMonthSpentCents = previousMonthSpent,
            currentMonthReceivedCents = currentMonthReceived,
            categoryBreakdown = categoryBreakdown,
            monthlyTrends = monthlyTrends,
            recentTransactions = activeFiltered.take(6)
        )
    }

    private fun calculateLastSixMonthsTrend(
        transactions: List<TransactionEntity>,
        nowMillis: Long
    ): List<MonthlyTrendPoint> {
        val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFormat = SimpleDateFormat("MMM", Locale.US)

        val buckets = linkedMapOf<String, String>()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, -5)

        repeat(6) {
            val date = Date(cal.timeInMillis)
            buckets[keyFormat.format(date)] = labelFormat.format(date)
            cal.add(Calendar.MONTH, 1)
        }

        val grouped = transactions.groupBy { keyFormat.format(Date(it.transactionDate)) }

        return buckets.map { (ymKey, label) ->
            val monthTxs = grouped[ymKey].orEmpty()
            var inc = 0L
            var exp = 0L
            var fees = 0L

            for (tx in monthTxs) {
                val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                    .getOrDefault(TransactionType.OTHER)
                fees += tx.charges
                when (type.financialClass) {
                    FinancialClass.INCOME -> inc += tx.amount
                    FinancialClass.EXPENSE, FinancialClass.TRANSFER -> exp += tx.amount
                    else -> Unit
                }
            }
            MonthlyTrendPoint(
                monthLabel = label,
                yearMonthKey = ymKey,
                incomeCents = inc,
                expenseCents = exp,
                chargesCents = fees
            )
        }
    }

    fun getStartOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfWeek(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long, monthOffset: Int = 0): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}