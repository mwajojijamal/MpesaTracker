package com.mpesatracker.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Handles all currency conversions between user-readable Kenyan Shillings (BigDecimal/String)
 * and database-safe minor units (Long cents).
 * Example: Ksh 500.00 <-> 50000L cents.
 */
object MoneyUtils {

    private val HUNDRED = BigDecimal(100)

    /**
     * Converts a string like "1,250.50" or "500.00" into cents (125050L or 50000L).
     */
    fun parseKshToCents(rawAmount: String): Long {
        val cleaned = rawAmount
            .replace("Ksh", "", ignoreCase = true)
            .replace(",", "")
            .trim()
        if (cleaned.isEmpty()) return 0L
        return try {
            BigDecimal(cleaned)
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .longValueExact()
        } catch (e: NumberFormatException) {
            0L
        }
    }

    /**
     * Converts database cents (50000L) back to BigDecimal (500.00).
     */
    fun centsToBigDecimal(cents: Long): BigDecimal {
        return BigDecimal.valueOf(cents).divide(HUNDRED, 2, RoundingMode.HALF_UP)
    }

    /**
     * Formats database cents (125050L) into "Ksh 1,250.50" for the UI.
     */
    fun formatCentsToKsh(cents: Long): String {
        val amount = centsToBigDecimal(cents)
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "Ksh ${formatter.format(amount)}"
    }
}