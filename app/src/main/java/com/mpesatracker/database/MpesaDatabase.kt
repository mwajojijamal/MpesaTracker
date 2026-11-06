package com.mpesatracker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mpesatracker.data.dao.BudgetDao
import com.mpesatracker.data.dao.CategorisationRuleDao
import com.mpesatracker.data.dao.CategoryDao
import com.mpesatracker.data.dao.TransactionDao
import com.mpesatracker.data.entities.BudgetEntity
import com.mpesatracker.data.entities.CategorisationRuleEntity
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        CategorisationRuleEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MpesaDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categorisationRuleDao(): CategorisationRuleDao

    companion object {
        const val DATABASE_NAME = "mpesa_finance_tracker.db"

        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(name = "Food and groceries", description = "Supermarkets, markets, and restaurants", icon = "restaurant", isDefault = true),
            CategoryEntity(name = "Transport", description = "Matatu, ride-hailing, fuel, and travel", icon = "directions_bus", isDefault = true),
            CategoryEntity(name = "Shopping", description = "Clothing, electronics, and household items", icon = "shopping_bag", isDefault = true),
            CategoryEntity(name = "Education", description = "School fees, books, and courses", icon = "school", isDefault = true),
            CategoryEntity(name = "Rent", description = "Monthly housing and rent payments", icon = "home", isDefault = true),
            CategoryEntity(name = "Utilities", description = "KPLC tokens, water, Wi-Fi, and gas", icon = "bolt", isDefault = true),
            CategoryEntity(name = "Entertainment", description = "Movies, subscriptions, and outings", icon = "movie", isDefault = true),
            CategoryEntity(name = "Healthcare", description = "Hospitals, clinics, and pharmacies", icon = "local_hospital", isDefault = true),
            CategoryEntity(name = "Airtime and data", description = "Safaricom airtime and data bundles", icon = "phone_android", isDefault = true),
            CategoryEntity(name = "Business", description = "Stock, business supplies, and services", icon = "business_center", isDefault = true),
            CategoryEntity(name = "Family and friends", description = "Money sent to family or friends", icon = "people", isDefault = true),
            CategoryEntity(name = "Other", description = "Uncategorised or miscellaneous transactions", icon = "category", isDefault = true)
        )
    }
}