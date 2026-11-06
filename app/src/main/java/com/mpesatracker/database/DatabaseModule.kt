package com.mpesatracker.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpesatracker.data.dao.BudgetDao
import com.mpesatracker.data.dao.CategorisationRuleDao
import com.mpesatracker.data.dao.CategoryDao
import com.mpesatracker.data.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMpesaDatabase(
        @ApplicationContext context: Context,
        categoryDaoProvider: Provider<CategoryDao>
    ): MpesaDatabase {
        return Room.databaseBuilder(
            context,
            MpesaDatabase::class.java,
            MpesaDatabase.DATABASE_NAME
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed default categories on first database creation
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        categoryDaoProvider.get().insertCategories(MpesaDatabase.DEFAULT_CATEGORIES)
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideTransactionDao(database: MpesaDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: MpesaDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideBudgetDao(database: MpesaDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideCategorisationRuleDao(database: MpesaDatabase): CategorisationRuleDao =
        database.categorisationRuleDao()
}