package com.mpesatracker.categorisation

import com.mpesatracker.data.entities.FinancialClass
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType

/**
 * Built-in offline keyword dictionary mapped to our 12 default category names,
 * plus classification helpers to distinguish actual expenditure from transfers/withdrawals.
 */
object CategorisationRules {

    val BUILT_IN_KEYWORD_MAP: Map<String, List<String>> = mapOf(
        "Food and groceries" to listOf(
            "NAIVAS", "QUICKMART", "CARREFOUR", "CHANDARANA", "CLEANSHELF",
            "MAGUNAS", "EASTMATT", "KFC", "JAVA HOUSE", "ARTCAFFE", "CHICKEN INN",
            "PIZZA INN", "GALITOS", "GLOVO", "BUTCHERY", "BAKERY", "RESTAURANT",
            "HOTEL", "CAFE", "GROCERY", "SUPERMARKET", "SOKO"
        ),
        "Transport" to listOf(
            "UBER", "BOLT", "LITTLE CAB", "FARAS", "TOTAL", "SHELL", "RUBIS",
            "OLA ENERGY", "NATIONAL OIL", "ASTROL", "LEXO", "SGR", "MADARAKA",
            "MATATU", "SACCO", "BUS", "SHUTTLE", "KENHA", "NTSA", "PARKING"
        ),
        "Utilities" to listOf(
            "KPLC", "KENYA POWER", "TOKENS", "PREPAID", "NAIROBI WATER",
            "THIKA WATER", "KIAMBU WATER", "ELDOWAS", "NYEWASCO", "ZUKU",
            "SAFARICOM HOME", "FAIBA", "STARLINK", "LIQUID", "POAPOA", "GAS", "GARBAGE"
        ),
        "Healthcare" to listOf(
            "HOSPITAL", "CLINIC", "PHARMACY", "CHEMIST", "GOODLIFE", "AGA KHAN",
            "MP SHAH", "NAIROBI WEST", "AVENUE HEALTH", "GERTRUDES", "DISPENSARY",
            "MEDICAL", "DENTAL", "OPTICAL", "NHIF", "SHIF", "SHA"
        ),
        "Education" to listOf(
            "SCHOOL", "UNIVERSITY", "COLLEGE", "ACADEMY", "INSTITUTE",
            "TUITION", "HELB", "TEXTBOOK", "BOOKSHOP", "KINDERGARTEN"
        ),
        "Rent" to listOf(
            "RENT", "LANDLORD", "APARTMENT", "HOUSING", "ESTATE", "CARETAKER", "PROPMAN"
        ),
        "Shopping" to listOf(
            "JUMIA", "KILIMALL", "LC WAIKIKI", "WOOLWORTHS", "MR PRICE",
            "MINISO", "DECATHLON", "HOTPOINT", "RAMTONS", "BOUTIQUE", "ELECTRONICS",
            "HARDWARE", "FURNITURE"
        ),
        "Entertainment" to listOf(
            "NETFLIX", "SHOWMAX", "SPOTIFY", "DSTV", "GOTV", "MULTICHOICE",
            "CINEMA", "IMAX", "CENTURY", "CLUB", "LOUNGE", "RESORT", "KENYABUZZ"
        ),
        "Airtime and data" to listOf(
            "SAFARICOM AIRTIME", "AIRTIME", "BUNDLES", "DATA BUNDLES", "TUNUKIWA"
        ),
        "Business" to listOf(
            "WHOLESALE", "DISTRIBUTORS", "SUPPLIERS", "ENTERPRISES", "KRA",
            "ECITIZEN", "COUNTY", "PERMIT", "LICENSE", "PRINTING", "STATIONERY"
        )
    )

    // Keywords indicating transfers between a user's own savings/bank accounts
    private val OWN_ACCOUNT_KEYWORDS = listOf(
        "M-SHWARI", "MSHWARI", "KCB M-PESA", "FULIZA", "ZIIDI", "EQUITY",
        "CO-OP", "COOPERATIVE BANK", "NCBA", "ABSA", "STANBIC", "STANCHART",
        "I&M", "DTB", "FAMILY BANK"
    )

    /**
     * Determines if a transaction is a transfer between the user's own bank/savings accounts.
     */
    fun isOwnAccountTransfer(partyName: String?): Boolean {
        if (partyName.isNullOrBlank()) return false
        val upper = partyName.uppercase()
        return OWN_ACCOUNT_KEYWORDS.any { upper.contains(it) }
    }

    /**
     * Evaluates whether a transaction should be counted as actual consumption/expenditure.
     * Excludes reversed/failed transactions, incoming money, cash withdrawals, and P2P transfers.
     */
    fun isActualConsumptionExpense(tx: TransactionEntity): Boolean {
        if (tx.status != TransactionStatus.COMPLETED.name) return false
        val type = runCatching { TransactionType.valueOf(tx.transactionType) }
            .getOrDefault(TransactionType.OTHER)
        return type.financialClass == FinancialClass.EXPENSE
    }
}