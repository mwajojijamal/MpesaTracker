package com.mpesatracker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mpesatracker.data.entities.CategorisationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorisationRuleDao {
    @Query("SELECT * FROM categorisation_rules WHERE isActive = 1 ORDER BY priority DESC")
    suspend fun getActiveRules(): List<CategorisationRuleEntity>

    @Query("SELECT * FROM categorisation_rules ORDER BY priority DESC, matchingKeyword ASC")
    fun observeAllRules(): Flow<List<CategorisationRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategorisationRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRules(rules: List<CategorisationRuleEntity>)

    @Update
    suspend fun updateRule(rule: CategorisationRuleEntity)

    @Delete
    suspend fun deleteRule(rule: CategorisationRuleEntity)
}