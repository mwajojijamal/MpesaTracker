package com.mpesatracker.ui.budgets

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mpesatracker.data.MoneyUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires 100% local Android notifications when a user reaches 80% or 100% of a budget.
 * Tracks notified budget IDs in memory per session to avoid spamming repeated alerts.
 */
@Singleton
class BudgetNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notifiedThresholds = mutableSetOf<String>()

    companion object {
        private const val CHANNEL_ID = "mpesa_budget_alerts"
        private const val CHANNEL_NAME = "Budget Limit Alerts"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Offline alerts when approaching or exceeding M-Pesa budgets"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotifyBudgetThreshold(
        budgetId: Long,
        budgetTitle: String,
        spentCents: Long,
        limitCents: Long
    ) {
        if (limitCents <= 0L) return
        val ratio = spentCents.toFloat() / limitCents.toFloat()

        val thresholdLevel = when {
            ratio >= 1.0f -> "EXCEEDED"
            ratio >= 0.8f -> "APPROACHING"
            else -> return
        }

        val dedupeKey = "${budgetId}_$thresholdLevel"
        if (!notifiedThresholds.add(dedupeKey)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val percent = (ratio * 100).toInt()
        val title = if (thresholdLevel == "EXCEEDED") {
            "Budget Exceeded: $budgetTitle ($percent%)"
        } else {
            "Approaching Budget Limit: $budgetTitle ($percent%)"
        }

        val message = "Spent ${MoneyUtils.formatCentsToKsh(spentCents)} of ${MoneyUtils.formatCentsToKsh(limitCents)}."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(budgetId.toInt(), notification)
    }
}