package com.personalexpenses.tracker.testing

import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.SeedData
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory [ExpenseDataSource] used by unit tests. It mirrors the filtering
 * and aggregation semantics of the Room implementation closely enough to test
 * the view models without an Android device.
 */
class FakeExpenseDataSource(
    categories: List<CategoryEntity> = SeedData.defaultCategories()
        .mapIndexed { index, category -> category.copy(id = index + 1L) }
) : ExpenseDataSource {

    private val transactionsState = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val categoriesState = MutableStateFlow(categories)
    private val budgetsState = MutableStateFlow<List<BudgetEntity>>(emptyList())

    private var nextTransactionId = 1_000L
    private var nextCategoryId = 10_000L
    private var nextBudgetId = 1L

    var addTransactionCalls: Int = 0
        private set
    var updateTransactionCalls: Int = 0
        private set
    var deleteTransactionCalls: Int = 0
        private set

    fun setTransactions(transactions: List<TransactionEntity>) {
        transactionsState.value = transactions
        nextTransactionId = (transactions.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    fun setCategories(categories: List<CategoryEntity>) {
        categoriesState.value = categories
    }

    fun setBudgets(budgets: List<BudgetEntity>) {
        budgetsState.value = budgets
        nextBudgetId = (budgets.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    fun transactions(): List<TransactionEntity> = transactionsState.value

    fun budgets(): List<BudgetEntity> = budgetsState.value

    fun categories(): List<CategoryEntity> = categoriesState.value

    private fun rows(): List<TransactionRow> {
        val byId = categoriesState.value.associateBy { it.id }
        return transactionsState.value.mapNotNull { transaction ->
            val category = byId[transaction.categoryId] ?: return@mapNotNull null
            TransactionRow(
                id = transaction.id,
                amountCents = transaction.amountCents,
                type = transaction.type,
                categoryId = transaction.categoryId,
                date = transaction.date,
                note = transaction.note,
                categoryName = category.name,
                categoryColor = category.colorHex,
                categoryIcon = category.iconKey
            )
        }
    }

    private fun filtered(filter: TransactionFilter): List<TransactionRow> = rows()
        .filter { it.date >= filter.startInclusive && it.date < filter.endExclusive }
        .filter { filter.type == null || it.type == filter.type }
        .filter { filter.categoryId == null || it.categoryId == filter.categoryId }
        .filter {
            filter.query.isEmpty() ||
                it.note.contains(filter.query, ignoreCase = true) ||
                it.categoryName.contains(filter.query, ignoreCase = true)
        }
        .sortedWith(compareByDescending<TransactionRow> { it.date }.thenByDescending { it.id })

    override fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionRow>> =
        combine(transactionsState, categoriesState) { _, _ -> filtered(filter) }

    override fun observeCategories(type: TransactionType?): Flow<List<CategoryEntity>> =
        categoriesState.map { list ->
            if (type == null) list else list.filter { it.type == type }
        }

    override suspend fun getTransactionRow(id: Long): TransactionRow? =
        rows().firstOrNull { it.id == id }

    override suspend fun addTransaction(draft: TransactionDraft): Long {
        addTransactionCalls++
        val id = nextTransactionId++
        transactionsState.value = transactionsState.value + TransactionEntity(
            id = id,
            amountCents = draft.amountCents,
            type = draft.type,
            categoryId = draft.categoryId,
            date = draft.date,
            note = draft.note
        )
        return id
    }

    override suspend fun updateTransaction(id: Long, draft: TransactionDraft) {
        updateTransactionCalls++
        transactionsState.value = transactionsState.value.map { transaction ->
            if (transaction.id != id) transaction
            else transaction.copy(
                amountCents = draft.amountCents,
                type = draft.type,
                categoryId = draft.categoryId,
                date = draft.date,
                note = draft.note
            )
        }
    }

    override suspend fun deleteTransaction(id: Long) {
        deleteTransactionCalls++
        transactionsState.value = transactionsState.value.filterNot { it.id == id }
    }

    override suspend fun restoreTransaction(row: TransactionRow): Long = addTransaction(
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
    ): PeriodTotals {
        val rows = rows().filter { it.date >= startInclusive && it.date < endExclusive }
        return PeriodTotals(
            incomeCents = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
            expenseCents = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        )
    }

    override suspend fun getCategoryTotals(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long
    ): List<CategorySpend> = rows()
        .filter {
            it.type == type && it.date >= startInclusive && it.date < endExclusive
        }
        .groupBy { it.categoryId }
        .map { (categoryId, group) ->
            CategorySpend(categoryId, group.sumOf { it.amountCents }, group.size)
        }

    override suspend fun getTransactions(
        startInclusive: Long,
        endExclusive: Long,
        type: TransactionType?,
        categoryId: Long?,
        query: String
    ): List<TransactionRow> = filtered(
        TransactionFilter(startInclusive, endExclusive, type, categoryId, query)
    )

    override suspend fun getAllTransactions(): List<TransactionRow> =
        rows().sortedWith(compareBy<TransactionRow> { it.date }.thenBy { it.id })

    override fun observeBudgets(yearMonth: Int): Flow<List<BudgetEntity>> =
        budgetsState.map { budgets -> budgets.filter { it.yearMonth == yearMonth } }

    override suspend fun getBudgets(yearMonth: Int): List<BudgetEntity> =
        budgetsState.value.filter { it.yearMonth == yearMonth }

    override suspend fun setBudget(yearMonth: Int, categoryId: Long, amountCents: Long) {
        val existing = budgetsState.value.firstOrNull {
            it.yearMonth == yearMonth && it.categoryId == categoryId
        }
        budgetsState.value = if (existing == null) {
            budgetsState.value + BudgetEntity(
                id = nextBudgetId++,
                yearMonth = yearMonth,
                categoryId = categoryId,
                amountCents = amountCents
            )
        } else {
            budgetsState.value.map {
                if (it.id == existing.id) it.copy(amountCents = amountCents) else it
            }
        }
    }

    override suspend fun removeBudget(yearMonth: Int, categoryId: Long) {
        budgetsState.value = budgetsState.value.filterNot {
            it.yearMonth == yearMonth && it.categoryId == categoryId
        }
    }

    override suspend fun getCategories(): List<CategoryEntity> = categoriesState.value

    override suspend fun addCategory(
        name: String,
        type: TransactionType,
        colorHex: String,
        iconKey: String
    ): CategorySaveResult {
        val cleanName = name.trim()
        if (categoriesState.value.any {
                it.type == type && it.name.equals(cleanName, ignoreCase = true)
            }
        ) {
            return CategorySaveResult.DuplicateName
        }
        val id = nextCategoryId++
        categoriesState.value = categoriesState.value + CategoryEntity(
            id = id,
            name = cleanName,
            type = type,
            colorHex = colorHex,
            iconKey = iconKey,
            isDefault = false,
            sortOrder = categoriesState.value.count { it.type == type }
        )
        return CategorySaveResult.Success(id)
    }

    override suspend fun updateCategory(category: CategoryEntity): CategorySaveResult {
        val clash = categoriesState.value.firstOrNull {
            it.type == category.type &&
                it.name.equals(category.name.trim(), ignoreCase = true) &&
                it.id != category.id
        }
        if (clash != null) return CategorySaveResult.DuplicateName
        categoriesState.value = categoriesState.value.map {
            if (it.id == category.id) category.copy(name = category.name.trim()) else it
        }
        return CategorySaveResult.Success(category.id)
    }

    override suspend fun deleteCategory(categoryId: Long): CategoryDeleteResult {
        val category = categoriesState.value.firstOrNull { it.id == categoryId }
            ?: return CategoryDeleteResult.NOT_FOUND
        if (transactionsState.value.any { it.categoryId == categoryId }) {
            return CategoryDeleteResult.IN_USE
        }
        budgetsState.value = budgetsState.value.filterNot { it.categoryId == categoryId }
        categoriesState.value = categoriesState.value.filterNot { it.id == category.id }
        return CategoryDeleteResult.DELETED
    }

    override suspend fun countTransactions(): Int = transactionsState.value.size

    override suspend fun deleteAllData() {
        transactionsState.value = emptyList()
        budgetsState.value = emptyList()
    }
}
