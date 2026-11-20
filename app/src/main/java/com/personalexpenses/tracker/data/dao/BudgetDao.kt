package com.personalexpenses.tracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalexpenses.tracker.data.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun observeForMonth(yearMonth: Int): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun getForMonth(yearMonth: Int): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND categoryId = :categoryId LIMIT 1")
    suspend fun find(yearMonth: Int, categoryId: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE yearMonth = :yearMonth AND categoryId = :categoryId")
    suspend fun delete(yearMonth: Int, categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
