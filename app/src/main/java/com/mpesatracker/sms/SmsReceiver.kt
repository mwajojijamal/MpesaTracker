package com.mpesatracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.mpesatracker.categorisation.CategoryEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for incoming SMS messages in real time.
 * Strictly filters for M-Pesa messages, parses them offline, and stores them in Room.
 * Never logs raw SMS contents to Logcat in production to protect user privacy.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var categoryEngine: CategoryEngine

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multi-part SMS messages by originating sender address
        val groupedBySender = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, parts) in groupedBySender) {
                    if (!MessageValidator.isValidMpesaSender(sender)) continue

                    val fullBody = parts.joinToString(separator = "") { it.displayMessageBody ?: "" }
                    val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                    if (MessageValidator.isValidMpesaMessageBody(fullBody)) {
                        val parsed = MpesaParser.parse(fullBody, fallbackTimestamp = timestamp)
                        if (parsed != null) {
                            categoryEngine.ingestParsedTransaction(parsed)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}