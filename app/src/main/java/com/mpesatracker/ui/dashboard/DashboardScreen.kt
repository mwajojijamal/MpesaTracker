package com.mpesatracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpesatracker.analytics.MonthlyTrendPoint
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.ui.theme.ExpenseRed
import com.mpesatracker.ui.theme.IncomeGreen
import com.mpesatracker.ui.transactions.TransactionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("M-Pesa Personal Finance", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 1. Period Selector Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = state.selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.label) }
                        )
                    }
                }
            }

            // 2. Hero Financial Summary Card (Received, Spent, Transferred, Charges)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${state.selectedPeriod.label} Overview",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryMetricColumn(
                                label = "Money Received",
                                value = MoneyUtils.formatCentsToKsh(summary.totalReceivedCents),
                                valueColor = Color(0xFFA5D6A7)
                            )
                            SummaryMetricColumn(
                                label = "Direct Expenses",
                                value = MoneyUtils.formatCentsToKsh(summary.totalSpentCents),
                                valueColor = Color(0xFFFFCDD2),
                                alignEnd = true
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryMetricColumn(
                                label = "Sent & Withdrawn",
                                value = MoneyUtils.formatCentsToKsh(
                                    summary.totalTransferredCents + summary.totalWithdrawalsCents
                                ),
                                valueColor = Color.White
                            )
                            SummaryMetricColumn(
                                label = "M-Pesa Fees",
                                value = MoneyUtils.formatCentsToKsh(summary.totalChargesCents),
                                valueColor = Color(0xFFFFE082),
                                alignEnd = true
                            )
                        }
                    }
                }
            }

            // 3. Daily, Weekly, and Month-over-Month Activity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Short-Term Expenditure Activity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Spent Today", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    MoneyUtils.formatCentsToKsh(summary.todaySpentCents),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Spent This Week", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    MoneyUtils.formatCentsToKsh(summary.thisWeekSpentCents),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val monthDiff = summary.currentMonthSpentCents - summary.previousMonthSpentCents
                        val comparisonText = if (summary.previousMonthSpentCents == 0L) {
                            "This Month Outflow: ${MoneyUtils.formatCentsToKsh(summary.currentMonthSpentCents)}"
                        } else if (monthDiff >= 0) {
                            "This month is +${MoneyUtils.formatCentsToKsh(monthDiff)} higher than last month"
                        } else {
                            "This month is ${MoneyUtils.formatCentsToKsh(-monthDiff)} lower than last month"
                        }
                        Text(
                            text = comparisonText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 4. Income vs Outflow Visual Bar Card
            item {
                val totalOutflow = summary.totalSpentCents + summary.totalTransferredCents + summary.totalChargesCents
                val maxVal = maxOf(summary.totalReceivedCents, totalOutflow, 1L).toFloat()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Income vs Total Outflow",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Income: ${MoneyUtils.formatCentsToKsh(summary.totalReceivedCents)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = IncomeGreen
                        )
                        LinearProgressIndicator(
                            progress = { (summary.totalReceivedCents / maxVal).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = IncomeGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Outflow (Spent + Sent + Fees): ${MoneyUtils.formatCentsToKsh(totalOutflow)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ExpenseRed
                        )
                        LinearProgressIndicator(
                            progress = { (totalOutflow / maxVal).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ExpenseRed
                        )
                    }
                }
            }

            // 5. Spending by Category Card
            if (summary.categoryBreakdown.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Top Spending Categories",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            summary.categoryBreakdown.take(5).forEach { catItem ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${catItem.categoryName} (${catItem.transactionCount})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = MoneyUtils.formatCentsToKsh(catItem.totalCents),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { catItem.percentageOfTotal.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. 6-Month Expenditure Trend Mini-Chart
            if (summary.monthlyTrends.isNotEmpty()) {
                item {
                    MonthlyTrendCard(trends = summary.monthlyTrends)
                }
            }

            // 7. Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToTransactions) {
                        Text("View All")
                    }
                }
            }

            if (summary.recentTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "No transactions recorded in this period yet. Go to the Transactions tab to sync your M-Pesa inbox or add a transaction.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(summary.recentTransactions, key = { it.id }) { tx ->
                    TransactionCard(
                        transaction = tx,
                        category = tx.categoryId?.let { state.categoryMap[it] },
                        onClick = onNavigateToTransactions
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    alignEnd: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun MonthlyTrendCard(trends: List<MonthlyTrendPoint>) {
    val maxExpense = trends.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1L) ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "6-Month Outflow Trend",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trends.forEach { point ->
                    val ratio = (point.expenseCents.toFloat() / maxExpense.toFloat()).coerceIn(0.05f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height((80 * ratio).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = point.monthLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}