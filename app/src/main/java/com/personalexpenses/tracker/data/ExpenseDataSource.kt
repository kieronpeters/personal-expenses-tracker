package com.personalexpenses.tracker.data

import com.personalexpenses.tracker.data.entity.BudgetEntity
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.CategoryDeleteResult
import com.personalexpenses.tracker.data.model.CategorySaveResult
import com.personalexpenses.tracker.data.model.CategorySpend
import com.personalexpenses.tracker.data.model.PeriodTotals
import com.personalexpenses.tracker.data.model.TransactionDraft
import com.personalexpenses.tracker.data.model.TransactionFilter
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * The persistence contract used by the UI layer. Keeping it as an interface
 * allows view models to be unit tested with a lightweight in-memory fake.
 */
interface ExpenseDataSource {

    fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionRow>>

    fun observeCategories(type: TransactionType? = null): Flow<List<CategoryEntity>>

    suspend fun getTransactionRow(id: Long): TransactionRow?

    suspend fun addTransaction(draft: TransactionDraft): Long

    suspend fun updateTransaction(id: Long, draft: TransactionDraft)

    suspend fun deleteTransaction(id: Long)

    suspend fun restoreTransaction(row: TransactionRow): Long

    suspend fun getPeriodTotals(startInclusive: Long, endExclusive: Long): PeriodTotals

    suspend fun getCategoryTotals(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long
    ): List<CategorySpend>

    suspend fun getTransactions(
        startInclusive: Long,
        endExclusive: Long,
        type: TransactionType? = null,
        categoryId: Long? = null,
        query: String = ""
    ): List<TransactionRow>

    suspend fun getAllTransactions(): List<TransactionRow>

    fun observeBudgets(yearMonth: Int): Flow<List<BudgetEntity>>

    suspend fun getBudgets(yearMonth: Int): List<BudgetEntity>

    suspend fun setBudget(yearMonth: Int, categoryId: Long, amountCents: Long)

    suspend fun removeBudget(yearMonth: Int, categoryId: Long)

    suspend fun getCategories(): List<CategoryEntity>

    suspend fun addCategory(
        name: String,
        type: TransactionType,
        colorHex: String,
        iconKey: String
    ): CategorySaveResult

    suspend fun updateCategory(category: CategoryEntity): CategorySaveResult

    suspend fun deleteCategory(categoryId: Long): CategoryDeleteResult

    suspend fun countTransactions(): Int

    suspend fun deleteAllData()
}
