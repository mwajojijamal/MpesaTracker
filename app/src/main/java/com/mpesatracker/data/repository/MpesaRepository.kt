package com.mpesatracker.data.repository

import com.mpesatracker.data.dao.BudgetDao
import com.mpesatracker.data.dao.CategorisationRuleDao
import com.mpesatracker.data.dao.CategoryDao
import com.mpesatracker.data.dao.TransactionDao
import com.mpesatracker.data.database.MpesaDatabase
import com.mpesatracker.data.entities.BudgetEntity
import com.mpesatracker.data.entities.CategorisationRuleEntity
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpesaRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val ruleDao: CategorisationRuleDao
) {
    // --- Transactions ---
    fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observeAllTransactions()

    fun observeTransactionsInPeriod(startDate: Long, endDate: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactionsInPeriod(startDate, endDate)

    suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.getTransactionById(id)

    suspend fun getTransactionByCode(code: String): TransactionEntity? =
        transactionDao.getTransactionByCode(code)

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.deleteTransaction(transaction)

    suspend fun markTransactionReversed(originalCode: String) =
        transactionDao.markTransactionReversed(originalCode)

    // --- Categories ---
    fun observeAllCategories(): Flow<List<CategoryEntity>> =
        categoryDao.observeAllCategories()

    suspend fun getAllCategories(): List<CategoryEntity> =
        categoryDao.getAllCategories()

    suspend fun ensureDefaultCategoriesSeeded() {
        if (categoryDao.getAllCategories().isEmpty()) {
            categoryDao.insertCategories(MpesaDatabase.DEFAULT_CATEGORIES)
        }
    }

    suspend fun insertCategory(category: CategoryEntity): Long =
        categoryDao.insertCategory(category)

    // --- Categorisation Rules ---
    fun observeAllRules(): Flow<List<CategorisationRuleEntity>> =
        ruleDao.observeAllRules()

    suspend fun getActiveRules(): List<CategorisationRuleEntity> =
        ruleDao.getActiveRules()

    suspend fun saveMerchantRule(keyword: String, categoryId: Long) {
        val cleaned = keyword.trim().uppercase()
        if (cleaned.length >= 3) {
            ruleDao.insertRule(
                CategorisationRuleEntity(
                    matchingKeyword = cleaned,
                    categoryId = categoryId,
                    priority = 10,
                    isActive = true
                )
            )
        }
    }

    // --- Budgets ---
    fun observeAllBudgets(): Flow<List<BudgetEntity>> =
        budgetDao.observeAllBudgets()

    suspend fun insertBudget(budget: BudgetEntity): Long =
        budgetDao.insertBudget(budget)

    suspend fun deleteBudget(budget: BudgetEntity) =
        budgetDao.deleteBudget(budget)
}