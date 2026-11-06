package com.mpesatracker.analytics

import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.FinancialClass
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class AnalyticsGranularity(val label: String) {
    DAILY("Daily (7D)"),
    WEEKLY("Weekly (4W)"),
    MONTHLY("Monthly (6M)"),
    YEARLY("Yearly")
}

data class TimeBucketPoint(
    val label: String,
    val expenseCents: Long,
    val incomeCents: Long
)

data class MerchantSummary(
    val merchantName: String,
    val totalCents: Long,
    val transactionCount: Int
)

data class FinancialInsight(
    val title: String,
    val description: String
)

data class AnalyticsReport(
    val totalIncomeCents: Long = 0L,
    val totalExpenseCents: Long = 0L,
    val totalChargesCents: Long = 0L,
    val averageDailyOutflowCents: Long = 0L,
    val totalTransactionCount: Int = 0,
    val timeBuckets: List<TimeBucketPoint> = emptyList(),
    val categoryBreakdown: List<CategorySpendingItem> = emptyList(),
    val topMerchantsBySpend: List<MerchantSummary> = emptyList(),
    val mostFrequentRecipients: List<MerchantSummary> = emptyList(),
    val highestIndividualExpenses: List<TransactionEntity> = emptyList(),
    val insights: List<FinancialInsight> = emptyList()
)

object AnalyticsEngine {

    fun generateReport(
        allTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        granularity: AnalyticsGranularity,
        nowMillis: Long = System.currentTimeMillis()
    ): AnalyticsReport {
        val completed = allTransactions.filter { it.status == TransactionStatus.COMPLETED.name }
        if (completed.isEmpty()) return AnalyticsReport()

        val categoryMap = categories.associateBy { it.id }

        var totalIncome = 0L
        var totalOutflow = 0L
        var totalCharges = 0L

        val outflowTxs = mutableListOf<TransactionEntity>()

        for (tx in completed) {
            val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                .getOrDefault(TransactionType.OTHER)
            totalCharges += tx.charges

            when (type.financialClass) {
                FinancialClass.INCOME -> totalIncome += tx.amount
                FinancialClass.EXPENSE, FinancialClass.TRANSFER, FinancialClass.WITHDRAWAL -> {
                    totalOutflow += tx.amount
                    outflowTxs.add(tx)
                }
                else -> Unit
            }
        }

        // Average Daily Outflow across the span of recorded transactions
        val oldestTimestamp = completed.minOf { it.transactionDate }
        val daysSpan = max(1L, ((nowMillis - oldestTimestamp) / (1000L * 60 * 60 * 24)) + 1L)
        val averageDailyOutflow = totalOutflow / daysSpan

        // Category breakdown
        val safeTotalOutflow = max(1L, totalOutflow)
        val categoryBreakdown = outflowTxs
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val sum = list.sumOf { it.amount }
                CategorySpendingItem(
                    categoryId = catId,
                    categoryName = catId?.let { categoryMap[it]?.name } ?: "Uncategorised",
                    totalCents = sum,
                    transactionCount = list.size,
                    percentageOfTotal = sum.toFloat() / safeTotalOutflow.toFloat()
                )
            }
            .sortedByDescending { it.totalCents }

        // Merchant & Recipient rankings
        val merchantGroups = outflowTxs
            .filter { !it.recipient.isNullOrBlank() }
            .groupBy { it.recipient!!.trim().uppercase() }
            .map { (name, list) ->
                MerchantSummary(
                    merchantName = name,
                    totalCents = list.sumOf { it.amount },
                    transactionCount = list.size
                )
            }

        val topMerchantsBySpend = merchantGroups.sortedByDescending { it.totalCents }.take(6)
        val mostFrequentRecipients = merchantGroups.sortedByDescending { it.transactionCount }.take(6)

        // Highest individual expenses
        val highestIndividual = outflowTxs.sortedByDescending { it.amount }.take(5)

        // Time-series buckets based on selected granularity
        val timeBuckets = buildTimeBuckets(completed, granularity, nowMillis)

        // Rule-based Financial Insights (Section 3I)
        val insights = generateInsights(
            completed = completed,
            outflowTxs = outflowTxs,
            categoryBreakdown = categoryBreakdown,
            topMerchants = topMerchantsBySpend,
            totalIncome = totalIncome,
            totalOutflow = totalOutflow,
            totalCharges = totalCharges,
            nowMillis = nowMillis
        )

        return AnalyticsReport(
            totalIncomeCents = totalIncome,
            totalExpenseCents = totalOutflow,
            totalChargesCents = totalCharges,
            averageDailyOutflowCents = averageDailyOutflow,
            totalTransactionCount = completed.size,
            timeBuckets = timeBuckets,
            categoryBreakdown = categoryBreakdown,
            topMerchantsBySpend = topMerchantsBySpend,
            mostFrequentRecipients = mostFrequentRecipients,
            highestIndividualExpenses = highestIndividual,
            insights = insights
        )
    }

    private fun buildTimeBuckets(
        transactions: List<TransactionEntity>,
        granularity: AnalyticsGranularity,
        nowMillis: Long
    ): List<TimeBucketPoint> {
        return when (granularity) {
            AnalyticsGranularity.DAILY -> {
                val dayFormat = SimpleDateFormat("EEE", Locale.US)
                val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                val keys = linkedMapOf<String, String>()
                repeat(7) {
                    val d = Date(cal.timeInMillis)
                    keys[keyFormat.format(d)] = dayFormat.format(d)
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                val grouped = transactions.groupBy { keyFormat.format(Date(it.transactionDate)) }
                keys.map { (k, label) -> summarizeBucket(label, grouped[k].orEmpty()) }
            }

            AnalyticsGranularity.WEEKLY -> {
                val result = mutableListOf<TimeBucketPoint>()
                val oneDayMillis = 24 * 60 * 60 * 1000L
                for (w in 3 downTo 0) {
                    val end = nowMillis - (w * 7 * oneDayMillis)
                    val start = end - (7 * oneDayMillis)
                    val label = if (w == 0) "This Wk" else "${w}w ago"
                    val slice = transactions.filter { it.transactionDate in (start + 1)..end }
                    result.add(summarizeBucket(label, slice))
                }
                result
            }

            AnalyticsGranularity.MONTHLY -> {
                val labelFormat = SimpleDateFormat("MMM", Locale.US)
                val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -5)
                }
                val keys = linkedMapOf<String, String>()
                repeat(6) {
                    val d = Date(cal.timeInMillis)
                    keys[keyFormat.format(d)] = labelFormat.format(d)
                    cal.add(Calendar.MONTH, 1)
                }
                val grouped = transactions.groupBy { keyFormat.format(Date(it.transactionDate)) }
                keys.map { (k, label) -> summarizeBucket(label, grouped[k].orEmpty()) }
            }

            AnalyticsGranularity.YEARLY -> {
                val keyFormat = SimpleDateFormat("yyyy", Locale.US)
                val grouped = transactions.groupBy { keyFormat.format(Date(it.transactionDate)) }
                grouped.keys.sorted().takeLast(5).map { yr ->
                    summarizeBucket(yr, grouped[yr].orEmpty())
                }
            }
        }
    }

    private fun summarizeBucket(label: String, list: List<TransactionEntity>): TimeBucketPoint {
        var inc = 0L
        var exp = 0L
        for (tx in list) {
            val type = runCatching { TransactionType.valueOf(tx.transactionType) }
                .getOrDefault(TransactionType.OTHER)
            when (type.financialClass) {
                FinancialClass.INCOME -> inc += tx.amount
                FinancialClass.EXPENSE, FinancialClass.TRANSFER, FinancialClass.WITHDRAWAL -> exp += tx.amount
                else -> Unit
            }
        }
        return TimeBucketPoint(label = label, expenseCents = exp, incomeCents = inc)
    }

    private fun generateInsights(
        completed: List<TransactionEntity>,
        outflowTxs: List<TransactionEntity>,
        categoryBreakdown: List<CategorySpendingItem>,
        topMerchants: List<MerchantSummary>,
        totalIncome: Long,
        totalOutflow: Long,
        totalCharges: Long,
        nowMillis: Long
    ): List<FinancialInsight> {
        val list = mutableListOf<FinancialInsight>()

        // 1. Top category insight
        categoryBreakdown.firstOrNull()?.let { topCat ->
            val pct = (topCat.percentageOfTotal * 100).toInt()
            list.add(
                FinancialInsight(
                    title = "Highest Spending Category: ${topCat.categoryName}",
                    description = "${topCat.categoryName} consumes $pct% of your total outflow (${MoneyUtils.formatCentsToKsh(topCat.totalCents)} across ${topCat.transactionCount} transactions)."
                )
            )
        }

        // 2. Top merchant/recipient insight
        topMerchants.firstOrNull()?.let { topMerchant ->
            list.add(
                FinancialInsight(
                    title = "Top Recipient: ${topMerchant.merchantName}",
                    description = "You have sent or paid ${MoneyUtils.formatCentsToKsh(topMerchant.totalCents)} to ${topMerchant.merchantName} (${topMerchant.transactionCount} times)."
                )
            )
        }

        // 3. This Month vs Last Month comparison
        val startOfThisMonth = SpendingCalculator.getStartOfMonth(nowMillis, 0)
        val startOfLastMonth = SpendingCalculator.getStartOfMonth(nowMillis, -1)
        val thisMonthSpend = outflowTxs.filter { it.transactionDate >= startOfThisMonth }.sumOf { it.amount }
        val lastMonthSpend = outflowTxs.filter { it.transactionDate in startOfLastMonth until startOfThisMonth }.sumOf { it.amount }

        val monthComparisonDesc = if (lastMonthSpend == 0L) {
            "You have spent ${MoneyUtils.formatCentsToKsh(thisMonthSpend)} so far this month."
        } else {
            val diff = thisMonthSpend - lastMonthSpend
            val direction = if (diff >= 0) "more" else "less"
            "You have spent ${MoneyUtils.formatCentsToKsh(thisMonthSpend)} this month, which is ${MoneyUtils.formatCentsToKsh(kotlin.math.abs(diff))} $direction than last month (${MoneyUtils.formatCentsToKsh(lastMonthSpend)})."
        }
        list.add(
            FinancialInsight(
                title = "Monthly Comparison",
                description = monthComparisonDesc
            )
        )

        // 4. Highest spending day of the week
        if (outflowTxs.isNotEmpty()) {
            val dayNameFormat = SimpleDateFormat("EEEE", Locale.US)
            val busiestDay = outflowTxs
                .groupBy { dayNameFormat.format(Date(it.transactionDate)) }
                .maxByOrNull { (_, txs) -> txs.sumOf { it.amount } }

            busiestDay?.let { (dayName, txs) ->
                list.add(
                    FinancialInsight(
                        title = "Highest Spending Day: $dayName",
                        description = "Your highest cumulative outflows happen on ${dayName}s, totaling ${MoneyUtils.formatCentsToKsh(txs.sumOf { it.amount })}."
                    )
                )
            }
        }

        // 5. Transaction Fee Burden
        list.add(
            FinancialInsight(
                title = "M-Pesa Transaction Fees",
                description = "You have paid ${MoneyUtils.formatCentsToKsh(totalCharges)} in M-Pesa transaction charges across ${completed.size} transactions."
            )
        )

        // 6. Income vs Outflow Net Position
        val netFlow = totalIncome - (totalOutflow + totalCharges)
        list.add(
            FinancialInsight(
                title = "Received vs Spent Balance",
                description = "Received: ${MoneyUtils.formatCentsToKsh(totalIncome)} | Outflow + Fees: ${MoneyUtils.formatCentsToKsh(totalOutflow + totalCharges)} | Net: ${MoneyUtils.formatCentsToKsh(netFlow)}."
            )
        )

        return list
    }
}