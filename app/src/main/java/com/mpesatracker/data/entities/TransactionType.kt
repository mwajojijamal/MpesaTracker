package com.mpesatracker.data.entities

/**
 * Distinguishes between actual expenses, income, transfers, withdrawals, fees, and reversals
 * as required by Section 5 (Transaction Classification Logic).
 */
enum class TransactionType(val displayName: String, val financialClass: FinancialClass) {
    RECEIVED("Money Received", FinancialClass.INCOME),
    SEND("Money Sent", FinancialClass.TRANSFER),
    BUY_GOODS("Buy Goods (Till)", FinancialClass.EXPENSE),
    PAYBILL("PayBill", FinancialClass.EXPENSE),
    WITHDRAWAL("Cash Withdrawal", FinancialClass.WITHDRAWAL),
    AIRTIME("Airtime & Bundles", FinancialClass.EXPENSE),
    REVERSAL("Reversal", FinancialClass.REVERSAL),
    OTHER("Other", FinancialClass.EXPENSE)
}

enum class FinancialClass {
    INCOME,
    EXPENSE,
    TRANSFER,
    WITHDRAWAL,
    FEES,
    REVERSAL
}

enum class TransactionStatus {
    COMPLETED,
    REVERSED,
    FAILED
}