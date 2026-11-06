package com.mpesatracker.sms

import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedMpesaTransaction(
    val transactionCode: String,
    val amountCents: Long,
    val chargesCents: Long,
    val transactionType: TransactionType,
    val sender: String?,
    val recipient: String?,
    val accountReference: String?,
    val transactionDateMillis: Long,
    val reversedOriginalCode: String? = null,
    val rawMessage: String
) {
    fun toEntity(categoryId: Long? = null): TransactionEntity {
        return TransactionEntity(
            transactionCode = transactionCode,
            amount = amountCents,
            transactionType = transactionType.name,
            sender = sender,
            recipient = recipient,
            charges = chargesCents,
            categoryId = categoryId,
            accountReference = accountReference,
            transactionDate = transactionDateMillis,
            rawMessage = rawMessage,
            status = TransactionStatus.COMPLETED.name,
            isVerified = true
        )
    }
}

/**
 * Dedicated offline parsing engine that converts raw M-Pesa SMS messages
 * into structured ParsedMpesaTransaction records.
 */
object MpesaParser {

    // 1. Transaction code at start: e.g., "ABC123XYZ Confirmed."
    private val CODE_REGEX = Regex(
        pattern = "^([A-Z0-9]{8,12})\\s+Confirmed\\.?",
        option = RegexOption.IGNORE_CASE
    )

    // 2. Transaction cost / fee: e.g., "Transaction cost, Ksh12.00." or "Transaction cost Ksh12.00"
    private val COST_REGEX = Regex(
        pattern = "Transaction\\s+cost[,:\\s]*Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        option = RegexOption.IGNORE_CASE
    )

    // 3. Date & Time: e.g., "on 31/10/2026 at 10:30 AM" or "on 23/6/23 at 3:41 PM"
    private val DATE_TIME_REGEX = Regex(
        pattern = "on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)",
        option = RegexOption.IGNORE_CASE
    )

    // 4. PayBill: "Ksh2,500.00 sent to KPLC PREPAID for account 14253647 on..."
    private val PAYBILL_REGEX = Regex(
        pattern = "Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+sent\\s+to\\s+(.+?)\\s+for\\s+account\\s+(.+?)\\s+on\\s+\\d{1,2}/",
        option = RegexOption.IGNORE_CASE
    )

    // 5. Send Money (P2P / Pochi): "Ksh500.00 sent to JOHN DOE 0712345678 on..." or "...sent to JOHN DOE on..."
    private val SEND_REGEX = Regex(
        pattern = "Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+sent\\s+to\\s+(.+?)\\s+on\\s+\\d{1,2}/",
        option = RegexOption.IGNORE_CASE
    )

    // 6. Buy Goods (Till): "Ksh1,250.00 paid to NAIVAS SUPERMARKET. on..."
    private val BUY_GOODS_REGEX = Regex(
        pattern = "Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+paid\\s+to\\s+(.+?)\\s+on\\s+\\d{1,2}/",
        option = RegexOption.IGNORE_CASE
    )

    // 7. Money Received: "You have received Ksh3,000.00 from PETER KAMAU 0722000111 on..."
    private val RECEIVED_REGEX = Regex(
        pattern = "You\\s+have\\s+received\\s+Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+?)(?:\\s+on\\s+\\d{1,2}/|\\.\\s*New\\s+M-PESA)",
        option = RegexOption.IGNORE_CASE
    )

    // 8. Cash Withdrawal: "...Withdraw Ksh1,000.00 from 123456 - SIMBA ENTERPRISES AGENT New M-PESA..."
    private val WITHDRAW_REGEX = Regex(
        pattern = "Withdraw\\s+Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+?)\\s+New\\s+M-PESA",
        option = RegexOption.IGNORE_CASE
    )

    // 9. Airtime Purchase: "You bought Ksh100.00 of airtime..."
    private val AIRTIME_REGEX = Regex(
        pattern = "You\\s+bought\\s+Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+of\\s+airtime",
        option = RegexOption.IGNORE_CASE
    )

    // 10. Reversal: "Reversal of transaction ABC123XYZ has been successfully reversed... and Ksh500.00 is credited"
    private val REVERSAL_REGEX = Regex(
        pattern = "Reversal\\s+of\\s+transaction\\s+([A-Z0-9]{8,12})\\s+has\\s+been\\s+successfully\\s+reversed.*?Ksh\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        option = RegexOption.IGNORE_CASE
    )

    // Trailing phone numbers on names, e.g., "JOHN DOE 0712345678" -> "JOHN DOE"
    private val TRAILING_PHONE_REGEX = Regex("\\s+(?:07|01|\\+?254)\\d[\\d*]{5,10}$")

    fun parse(rawSms: String, fallbackTimestamp: Long = System.currentTimeMillis()): ParsedMpesaTransaction? {
        val cleanedSms = rawSms.trim().replace(Regex("\\s+"), " ")
        if (!MessageValidator.isValidMpesaMessageBody(cleanedSms)) return null

        val codeMatch = CODE_REGEX.find(cleanedSms)
        val transactionCode = codeMatch?.groupValues?.get(1)?.uppercase() ?: return null

        val chargesCents = COST_REGEX.find(cleanedSms)?.groupValues?.get(1)
            ?.let { MoneyUtils.parseKshToCents(it) } ?: 0L

        val timestamp = parseDateTime(cleanedSms, fallbackTimestamp)

        // Check 1: Reversal
        REVERSAL_REGEX.find(cleanedSms)?.let { match ->
            val originalCode = match.groupValues[1].uppercase()
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[2])
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = 0L,
                transactionType = TransactionType.REVERSAL,
                sender = "M-PESA REVERSAL ($originalCode)",
                recipient = null,
                accountReference = originalCode,
                transactionDateMillis = timestamp,
                reversedOriginalCode = originalCode,
                rawMessage = rawSms.trim()
            )
        }

        // Check 2: PayBill (Must be checked before generic SEND because both use "sent to")
        PAYBILL_REGEX.find(cleanedSms)?.let { match ->
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[1])
            val merchant = cleanPartyName(match.groupValues[2])
            val account = match.groupValues[3].trim().trimEnd('.')
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = chargesCents,
                transactionType = TransactionType.PAYBILL,
                sender = null,
                recipient = merchant,
                accountReference = account,
                transactionDateMillis = timestamp,
                rawMessage = rawSms.trim()
            )
        }

        // Check 3: Money Sent (P2P / Pochi)
        SEND_REGEX.find(cleanedSms)?.let { match ->
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[1])
            val recipient = cleanPartyName(match.groupValues[2])
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = chargesCents,
                transactionType = TransactionType.SEND,
                sender = null,
                recipient = recipient,
                accountReference = null,
                transactionDateMillis = timestamp,
                rawMessage = rawSms.trim()
            )
        }

        // Check 4: Buy Goods and Services (Till)
        BUY_GOODS_REGEX.find(cleanedSms)?.let { match ->
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[1])
            val merchant = cleanPartyName(match.groupValues[2])
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = chargesCents,
                transactionType = TransactionType.BUY_GOODS,
                sender = null,
                recipient = merchant,
                accountReference = null,
                transactionDateMillis = timestamp,
                rawMessage = rawSms.trim()
            )
        }

        // Check 5: Money Received
        RECEIVED_REGEX.find(cleanedSms)?.let { match ->
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[1])
            val sender = cleanPartyName(match.groupValues[2])
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = chargesCents,
                transactionType = TransactionType.RECEIVED,
                sender = sender,
                recipient = null,
                accountReference = null,
                transactionDateMillis = timestamp,
                rawMessage = rawSms.trim()
            )
        }

        // Check 6: Cash Withdrawal
        WITHDRAW_REGEX.find(cleanedSms)?.let { match ->
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[1])
            val agent = cleanPartyName(match.groupValues[2])
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = chargesCents,
                transactionType = TransactionType.WITHDRAWAL,
                sender = null,
                recipient = agent,
                accountReference = null,
                transactionDateMillis = timestamp,
                rawMessage = rawSms.trim()
            )
        }

        // Check 7: Airtime Purchase
        AIRTIME_REGEX.find(cleanedSms)?.let { match ->
            val amountCents = MoneyUtils.parseKshToCents(match.groupValues[1])
            return ParsedMpesaTransaction(
                transactionCode = transactionCode,
                amountCents = amountCents,
                chargesCents = chargesCents,
                transactionType = TransactionType.AIRTIME,
                sender = null,
                recipient = "Safaricom Airtime",
                accountReference = null,
                transactionDateMillis = timestamp,
                rawMessage = rawSms.trim()
            )
        }

        return null
    }

    private fun cleanPartyName(rawName: String): String {
        return rawName
            .trim()
            .trimEnd('.')
            .replace(TRAILING_PHONE_REGEX, "")
            .trim()
    }

    private fun parseDateTime(sms: String, fallback: Long): Long {
        val match = DATE_TIME_REGEX.find(sms) ?: return fallback
        val datePart = match.groupValues[1].trim()
        val timePart = match.groupValues[2].trim().uppercase()
        val combined = "$datePart $timePart"

        val yearSegment = datePart.substringAfterLast('/')
        val pattern = if (yearSegment.length == 4) "d/M/yyyy h:mm a" else "d/M/yy h:mm a"

        return try {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
            }
            sdf.parse(combined)?.time ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}