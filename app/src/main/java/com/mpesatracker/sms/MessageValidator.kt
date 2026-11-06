package com.mpesatracker.sms

/**
 * Validates whether an incoming or inbox SMS is a genuine M-Pesa transaction message
 * rather than a promotional message, balance inquiry, or non-M-Pesa text.
 */
object MessageValidator {

    private val VALID_SENDERS = setOf("MPESA", "M-PESA")

    // Matches a 8-to-12 character alphanumeric transaction code at the start of the message
    private val TRANSACTION_CODE_PREFIX = Regex(
        pattern = "^[A-Z0-9]{8,12}\\s+Confirmed\\b",
        option = RegexOption.IGNORE_CASE
    )

    // Reversal messages sometimes start with the new code + Confirmed, or "Reversal of transaction..."
    private val REVERSAL_PATTERN = Regex(
        pattern = "\\b(Reversal of transaction|has been successfully reversed)\\b",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Checks if the SMS sender address belongs to Safaricom M-Pesa.
     */
    fun isValidMpesaSender(senderAddress: String?): Boolean {
        if (senderAddress.isNullOrBlank()) return false
        val normalized = senderAddress.trim().uppercase()
        return VALID_SENDERS.any { normalized.contains(it) }
    }

    /**
     * Checks if the SMS body looks like a parseable M-Pesa financial transaction.
     */
    fun isValidMpesaMessageBody(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val trimmed = body.trim()
        if (!trimmed.contains("Ksh", ignoreCase = true)) return false

        return TRANSACTION_CODE_PREFIX.containsMatchIn(trimmed) ||
            REVERSAL_PATTERN.containsMatchIn(trimmed)
    }
}