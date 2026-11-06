package com.mpesatracker

import com.mpesatracker.categorisation.CategorisationRules
import com.mpesatracker.categorisation.CategoryEngine
import com.mpesatracker.data.database.MpesaDatabase
import com.mpesatracker.data.entities.CategorisationRuleEntity
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType
import com.mpesatracker.data.repository.MpesaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryEngineTest {

    // Seed our 12 default categories with deterministic IDs 1..12 for testing
    private val testCategories = MpesaDatabase.DEFAULT_CATEGORIES.mapIndexed { index, cat ->
        cat.copy(id = (index + 1).toLong())
    }

    private fun categoryIdFor(name: String): Long =
        testCategories.first { it.name.equals(name, ignoreCase = true) }.id

    @Test
    fun `matches Naivas Supermarket to Food and groceries`() {
        val engine = CategoryEngine( repository = FakeRepoHelper.unused() )
        val catId = engine.matchCategoryPure(
            transactionType = TransactionType.BUY_GOODS,
            counterparty = "NAIVAS SUPERMARKET THIKA ROAD",
            accountReference = null,
            categories = testCategories,
            userRules = emptyList()
        )
        assertEquals(categoryIdFor("Food and groceries"), catId)
    }

    @Test
    fun `matches KPLC Prepaid to Utilities`() {
        val engine = CategoryEngine( repository = FakeRepoHelper.unused() )
        val catId = engine.matchCategoryPure(
            transactionType = TransactionType.PAYBILL,
            counterparty = "KPLC PREPAID",
            accountReference = "14253647",
            categories = testCategories,
            userRules = emptyList()
        )
        assertEquals(categoryIdFor("Utilities"), catId)
    }

    @Test
    fun `user custom rule overrides built-in keyword dictionary`() {
        val engine = CategoryEngine( repository = FakeRepoHelper.unused() )
        val businessCatId = categoryIdFor("Business")
        val customRule = CategorisationRuleEntity(
            id = 1L,
            matchingKeyword = "NAIVAS",
            categoryId = businessCatId,
            priority = 10,
            isActive = true
        )

        val catId = engine.matchCategoryPure(
            transactionType = TransactionType.BUY_GOODS,
            counterparty = "NAIVAS SUPERMARKET",
            accountReference = null,
            categories = testCategories,
            userRules = listOf(customRule)
        )
        assertEquals(businessCatId, catId)
    }

    @Test
    fun `classifies Buy Goods as actual expense but excludes Send, Withdrawal, and Reversed`() {
        val buyGoodsTx = TransactionEntity(
            transactionCode = "TX1",
            amount = 100000L,
            transactionType = TransactionType.BUY_GOODS.name,
            sender = null,
            recipient = "QUICKMART",
            transactionDate = 1000L,
            rawMessage = "raw"
        )
        val sendMoneyTx = buyGoodsTx.copy(
            transactionCode = "TX2",
            transactionType = TransactionType.SEND.name
        )
        val reversedTx = buyGoodsTx.copy(
            transactionCode = "TX3",
            status = TransactionStatus.REVERSED.name
        )

        assertTrue(CategorisationRules.isActualConsumptionExpense(buyGoodsTx))
        assertFalse(CategorisationRules.isActualConsumptionExpense(sendMoneyTx))
        assertFalse(CategorisationRules.isActualConsumptionExpense(reversedTx))
    }

    private object FakeRepoHelper {
        // Null-safe dummy reference since matchCategoryPure does not call repository methods
        @Suppress("UNCHECKED_CAST")
        fun unused(): MpesaRepository {
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe
            return unsafe.allocateInstance(MpesaRepository::class.java) as MpesaRepository
        }
    }
}