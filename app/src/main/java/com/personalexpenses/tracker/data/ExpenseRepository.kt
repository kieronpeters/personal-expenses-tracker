package com.personalexpenses.tracker.data

import com.personalexpenses.tracker.data.dao.BudgetDao
import com.personalexpenses.tracker.data.dao.CategoryDao
import com.personalexpenses.tracker.data.dao.TransactionDao
import com.personalexpenses.tracker.data.entity.BudgetEntity
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.entity.TransactionEntity
import com.personalexpenses.tracker.data.model.CategoryDeleteResult
import com.personalexpenses.tracker.data.model.CategorySaveResult
import com.personalexpenses.tracker.data.model.CategorySpend
import com.personalexpenses.tracker.data.model.PeriodTotals
import com.personalexpenses.tracker.data.model.TransactionDraft
import com.personalexpenses.tracker.data.model.TransactionFilter
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** Room backed implementation of [ExpenseDataSource]. */
class ExpenseRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ExpenseDataSource {

    override fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionRow>> =
        transactionDao.observeFiltered(
            startInclusive = filter.startInclusive,
            endExclusive = filter.endExclusive,
            type = filter.type,
            categoryId = filter.categoryId,
            query = filter.query
        )

    override fun observeCategories(type: TransactionType?): Flow<List<CategoryEntity>> =
        if (type == null) categoryDao.observeAll() else categoryDao.observeByType(type)

    override suspend fun getTransactionRow(id: Long): TransactionRow? = transactionDao.findRowById(id)

    override suspend fun addTransaction(draft: TransactionDraft): Long {
        val timestamp = now()
        return transactionDao.insert(
            TransactionEntity(
                amountCents = draft.amountCents,
                type = draft.type,
                categoryId = draft.categoryId,
                date = draft.date,
                note = draft.note.trim(),
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
    }

    override suspend fun updateTransaction(id: Long, draft: TransactionDraft) {
        val existing = transactionDao.findById(id)
            ?: throw IllegalArgumentException("Transaction $id does not exist")
        transactionDao.update(
            existing.copy(
                amountCents = draft.amountCents,
                type = draft.type,
                categoryId = draft.categoryId,
                date = draft.date,
                note = draft.note.trim(),
                updatedAt = now()
            )
        )
    }

    override suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
    }

    override suspend fun restoreTransaction(row: TransactionRow): Long =
        addTransaction(
            TransactionDraft(
                amountCents = row.amountCents,
                type = row.type,
                categoryId = row.categoryId,
                date = row.date,
                note = row.note
            )
        )

    override suspend fun getPeriodTotals(
        startInclusive: Long,
        endExclusive: Long
    ): PeriodTotals = PeriodTotals(
        incomeCents = transactionDao.sumByType(TransactionType.INCOME, startInclusive, endExclusive),
        expenseCents = transactionDao.sumByType(TransactionType.EXPENSE, startInclusive, endExclusive)
    )

    override suspend fun getCategoryTotals(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long
    ): List<CategorySpend> = transactionDao.categoryTotals(type, startInclusive, endExclusive)

    override suspend fun getTransactions(
        startInclusive: Long,
        endExclusive: Long,
        type: TransactionType?,
        categoryId: Long?,
        query: String
    ): List<TransactionRow> = transactionDao.findFiltered(
        startInclusive = startInclusive,
        endExclusive = endExclusive,
        type = type,
        categoryId = categoryId,
        query = query
    )

    override suspend fun getAllTransactions(): List<TransactionRow> = transactionDao.findAllRows()

    override fun observeBudgets(yearMonth: Int): Flow<List<BudgetEntity>> =
        budgetDao.observeForMonth(yearMonth)

    override suspend fun getBudgets(yearMonth: Int): List<BudgetEntity> =
        budgetDao.getForMonth(yearMonth)

    override suspend fun setBudget(yearMonth: Int, categoryId: Long, amountCents: Long) {
        require(amountCents >= 0) { "A budget cannot be negative" }
        val existing = budgetDao.find(yearMonth, categoryId)
        if (existing == null) {
            budgetDao.upsert(
                BudgetEntity(yearMonth = yearMonth, categoryId = categoryId, amountCents = amountCents)
            )
        } else {
            budgetDao.upsert(existing.copy(amountCents = amountCents))
        }
    }

    override suspend fun removeBudget(yearMonth: Int, categoryId: Long) {
        budgetDao.delete(yearMonth, categoryId)
    }

    override suspend fun getCategories(): List<CategoryEntity> = categoryDao.getAll()

    override suspend fun addCategory(
        name: String,
        type: TransactionType,
        colorHex: String,
        iconKey: String
    ): CategorySaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return CategorySaveResult.DuplicateName
        if (categoryDao.findByName(type, cleanName) != null) {
            return CategorySaveResult.DuplicateName
        }
        val nextOrder = categoryDao.getAll().filter { it.type == type }.size
        val id = categoryDao.insert(
            CategoryEntity(
                name = cleanName,
                type = type,
                colorHex = colorHex,
                iconKey = iconKey,
                isDefault = false,
                sortOrder = nextOrder
            )
        )
        return CategorySaveResult.Success(id)
    }

    override suspend fun updateCategory(category: CategoryEntity): CategorySaveResult {
        val cleanName = category.name.trim()
        if (cleanName.isEmpty()) return CategorySaveResult.DuplicateName
        val clash = categoryDao.findByName(category.type, cleanName)
        if (clash != null && clash.id != category.id) return CategorySaveResult.DuplicateName
        categoryDao.update(category.copy(name = cleanName))
        return CategorySaveResult.Success(category.id)
    }

    override suspend fun deleteCategory(categoryId: Long): CategoryDeleteResult {
        val category = categoryDao.findById(categoryId) ?: return CategoryDeleteResult.NOT_FOUND
        if (categoryDao.transactionCount(categoryId) > 0) return CategoryDeleteResult.IN_USE
        categoryDao.deleteBudgetsForCategory(categoryId)
        categoryDao.delete(category)
        return CategoryDeleteResult.DELETED
    }

    override suspend fun countTransactions(): Int = transactionDao.count()

    override suspend fun deleteAllData() {
        transactionDao.deleteAll()
        budgetDao.deleteAll()
    }
}
