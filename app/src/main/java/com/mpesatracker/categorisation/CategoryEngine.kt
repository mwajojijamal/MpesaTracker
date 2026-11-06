package com.mpesatracker.categorisation

import com.mpesatracker.data.entities.CategorisationRuleEntity
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionType
import com.mpesatracker.data.repository.MpesaRepository
import com.mpesatracker.sms.ParsedMpesaTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryEngine @Inject constructor(
    private val repository: MpesaRepository
) {

    /**
     * Suggests the best category ID for a transaction using 100% offline rule evaluation:
     * 1. Custom User Merchant Rules (highest priority)
     * 2. Transaction Type specific defaults (e.g., AIRTIME -> "Airtime and data")
     * 3. Built-in Kenyan Merchant Keyword Dictionary
     * 4. Fallback defaults ("Family and friends" for SEND, "Other" for everything else)
     */
    suspend fun suggestCategoryId(
        transactionType: TransactionType,
        counterparty: String?,
        accountReference: String? = null
    ): Long? {
        repository.ensureDefaultCategoriesSeeded()
        val categories = repository.getAllCategories()
        if (categories.isEmpty()) return null

        val activeUserRules = repository.getActiveRules()
        return matchCategoryPure(
            transactionType = transactionType,
            counterparty = counterparty,
            accountReference = accountReference,
            categories = categories,
            userRules = activeUserRules
        )
    }

    /**
     * Pure, testable matching function that requires no database connection during unit tests.
     */
    fun matchCategoryPure(
        transactionType: TransactionType,
        counterparty: String?,
        accountReference: String?,
        categories: List<CategoryEntity>,
        userRules: List<CategorisationRuleEntity>
    ): Long? {
        val categoryByName = categories.associateBy { it.name.lowercase() }
        val searchTarget = listOfNotNull(counterparty, accountReference)
            .joinToString(" ")
            .uppercase()

        // 1. Check user-created custom merchant rules first (sorted by priority DESC)
        if (searchTarget.isNotBlank()) {
            for (rule in userRules.sortedByDescending { it.priority }) {
                if (rule.isActive && searchTarget.contains(rule.matchingKeyword.uppercase())) {
                    return rule.categoryId
                }
            }
        }

        // 2. Direct TransactionType rules
        if (transactionType == TransactionType.AIRTIME) {
            return categoryByName["airtime and data"]?.id
        }

        // 3. Built-in Kenyan merchant keyword dictionary
        if (searchTarget.isNotBlank()) {
            for ((categoryName, keywords) in CategorisationRules.BUILT_IN_KEYWORD_MAP) {
                if (keywords.any { keyword -> searchTarget.contains(keyword) }) {
                    categoryByName[categoryName.lowercase()]?.id?.let { return it }
                }
            }
        }

        // 4. Sensible fallbacks by TransactionType
        return when (transactionType) {
            TransactionType.SEND -> categoryByName["family and friends"]?.id
            TransactionType.RECEIVED -> categoryByName["other"]?.id
            TransactionType.WITHDRAWAL -> categoryByName["other"]?.id
            TransactionType.REVERSAL -> categoryByName["other"]?.id
            else -> categoryByName["other"]?.id
        }
    }

    /**
     * Full end-to-end processor for a parsed M-Pesa SMS:
     * - Checks for duplicates by transactionCode
     * - If it's a REVERSAL, marks the original transaction as REVERSED so it isn't double-counted
     * - Assigns an automatic category
     * - Inserts into the local Room database
     * Returns true if a new transaction was inserted, false if it was a duplicate.
     */
    suspend fun ingestParsedTransaction(parsed: ParsedMpesaTransaction): Boolean {
        val existing = repository.getTransactionByCode(parsed.transactionCode)
        if (existing != null) {
            return false // Duplicate SMS detected; skip safely
        }

        // If this message reverses an earlier transaction, mark the original as REVERSED
        if (parsed.transactionType == TransactionType.REVERSAL && !parsed.reversedOriginalCode.isNullOrBlank()) {
            repository.markTransactionReversed(parsed.reversedOriginalCode)
        }

        val counterparty = parsed.recipient ?: parsed.sender
        val suggestedCatId = suggestCategoryId(
            transactionType = parsed.transactionType,
            counterparty = counterparty,
            accountReference = parsed.accountReference
        )

        val entity: TransactionEntity = parsed.toEntity(categoryId = suggestedCatId)
        val insertedRowId = repository.insertTransaction(entity)
        return insertedRowId != -1L
    }
}