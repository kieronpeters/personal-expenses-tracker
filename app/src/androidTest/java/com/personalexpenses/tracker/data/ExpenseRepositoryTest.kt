package com.personalexpenses.tracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.CategoryDeleteResult
import com.personalexpenses.tracker.data.model.CategorySaveResult
import com.personalexpenses.tracker.data.model.TransactionDraft
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.util.Dates
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [ExpenseRepository] against a real Room database:
 * transaction CRUD, undo support, aggregate queries, budget upsert semantics
 * and category lifecycle rules.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ExpenseRepository
    private lateinit var categories: List<CategoryEntity>
    private var nowValue: Long = NOW

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val categoryDao = database.categoryDao()
        categories = SeedData.defaultCategories().map { seed ->
            val id = categoryDao.insert(seed.copy(id = 0L))
            categoryDao.findById(id)!!
        }
        repository = ExpenseRepository(
            transactionDao = database.transactionDao(),
            categoryDao = categoryDao,
            budgetDao = database.budgetDao(),
            now = { nowValue }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun category(name: String): CategoryEntity =
        categories.first { it.name == name }

    private fun draft(
        cents: Long = 1_250L,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long = category("Food & Dining").id,
        note: String = "  Lunch  ",
        date: Long = Dates.startOfMonth(202611)
    ) = TransactionDraft(cents, type, categoryId, date, note)

    @Test
    fun addTransactionStoresTrimmedNoteAndTimestamps() = runBlocking {
        val id = repository.addTransaction(draft())

        val row = repository.getTransactionRow(id)
        assertThat(row).isNotNull()
        assertThat(row!!.amountCents).isEqualTo(1_250L)
        assertThat(row.note).isEqualTo("Lunch")
        assertThat(row.categoryName).isEqualTo("Food & Dining")

        val entity = database.transactionDao().findById(id)!!
        assertThat(entity.createdAt).isEqualTo(NOW)
        assertThat(entity.updatedAt).isEqualTo(NOW)
    }

    @Test
    fun updateTransactionChangesFieldsButKeepsCreatedAt() = runBlocking {
        val id = repository.addTransaction(draft())
        nowValue = NOW + 5_000L

        repository.updateTransaction(
            id,
            draft(
                cents = 9_900L,
                categoryId = category("Transport").id,
                note = "Taxi",
                date = Dates.startOfMonth(202611) + 86_400_000L
            )
        )

        val row = repository.getTransactionRow(id)!!
        assertThat(row.amountCents).isEqualTo(9_900L)
        assertThat(row.categoryName).isEqualTo("Transport")
        assertThat(row.note).isEqualTo("Taxi")

        val entity = database.transactionDao().findById(id)!!
        assertThat(entity.createdAt).isEqualTo(NOW)
        assertThat(entity.updatedAt).isEqualTo(NOW + 5_000L)
    }

    @Test
    fun updateMissingTransactionFails() = runBlocking {
        val failure = runCatching { repository.updateTransaction(999L, draft()) }
        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun deleteAndRestoreTransaction() = runBlocking {
        val id = repository.addTransaction(draft())
        val beforeDelete = repository.getTransactionRow(id)!!

        repository.deleteTransaction(id)
        assertThat(repository.getTransactionRow(id)).isNull()

        val restoredId = repository.restoreTransaction(beforeDelete)
        val restored = repository.getTransactionRow(restoredId)!!
        assertThat(restored.amountCents).isEqualTo(beforeDelete.amountCents)
        assertThat(restored.categoryId).isEqualTo(beforeDelete.categoryId)
        assertThat(restored.date).isEqualTo(beforeDelete.date)
        assertThat(restored.note).isEqualTo(beforeDelete.note)
    }

    @Test
    fun periodAndCategoryTotals() = runBlocking {
        val start = Dates.startOfMonth(202611)
        val end = Dates.endOfMonthExclusive(202611)
        repository.addTransaction(draft(cents = 1_000L, date = start + 1))
        repository.addTransaction(
            draft(cents = 2_000L, categoryId = category("Transport").id, date = start + 2)
        )
        repository.addTransaction(
            draft(
                cents = 5_000L,
                type = TransactionType.INCOME,
                categoryId = category("Salary").id,
                date = start + 3
            )
        )
        // Different month must not be included.
        repository.addTransaction(draft(cents = 7_000L, date = Dates.startOfMonth(202610)))

        val totals = repository.getPeriodTotals(start, end)
        assertThat(totals.expenseCents).isEqualTo(3_000L)
        assertThat(totals.incomeCents).isEqualTo(5_000L)
        assertThat(totals.netCents).isEqualTo(2_000L)

        val categoryTotals = repository.getCategoryTotals(
            TransactionType.EXPENSE,
            start,
            end
        )
        assertThat(categoryTotals).hasSize(2)
        assertThat(categoryTotals.sumOf { it.totalCents }).isEqualTo(3_000L)
    }

    @Test
    fun budgetUpsertKeepsOneRowPerCategoryAndMonth() = runBlocking {
        repository.setBudget(202611, 0L, 100_000L)
        repository.setBudget(202611, 0L, 150_000L)
        repository.setBudget(202611, category("Food & Dining").id, 20_000L)

        val budgets = repository.getBudgets(202611)
        assertThat(budgets).hasSize(2)
        val overall = budgets.first { it.categoryId == 0L }
        assertThat(overall.amountCents).isEqualTo(150_000L)

        // A different month is independent.
        repository.setBudget(202612, 0L, 90_000L)
        assertThat(repository.getBudgets(202612)).hasSize(1)
        assertThat(repository.getBudgets(202611)).hasSize(2)

        repository.removeBudget(202611, 0L)
        assertThat(repository.getBudgets(202611)).hasSize(1)
    }

    @Test
    fun categoriesAreUniquePerTypeAndProtectedWhileInUse() = runBlocking {
        val created = repository.addCategory("Coffee", TransactionType.EXPENSE, "#EF4444", "☕")
        assertThat(created).isInstanceOf(CategorySaveResult.Success::class.java)

        val duplicate = repository.addCategory(
            "coffee",
            TransactionType.EXPENSE,
            "#EF4444",
            "☕"
        )
        assertThat(duplicate).isEqualTo(CategorySaveResult.DuplicateName)

        val sameNameOtherType = repository.addCategory(
            "Coffee",
            TransactionType.INCOME,
            "#10B981",
            "☕"
        )
        assertThat(sameNameOtherType).isInstanceOf(CategorySaveResult.Success::class.java)

        val coffee = repository.getCategories().first { it.name == "Coffee" && it.type == TransactionType.EXPENSE }
        assertThat(repository.deleteCategory(coffee.id)).isEqualTo(CategoryDeleteResult.DELETED)
        assertThat(repository.deleteCategory(coffee.id)).isEqualTo(CategoryDeleteResult.NOT_FOUND)

        val food = category("Food & Dining")
        repository.addTransaction(draft(categoryId = food.id))
        assertThat(repository.deleteCategory(food.id)).isEqualTo(CategoryDeleteResult.IN_USE)
        assertThat(repository.getCategories().any { it.id == food.id }).isTrue()
    }

    @Test
    fun deleteCategoryAlsoRemovesItsBudgets() = runBlocking {
        val food = category("Food & Dining")
        repository.setBudget(202611, food.id, 20_000L)
        assertThat(repository.getBudgets(202611)).hasSize(1)

        assertThat(repository.deleteCategory(food.id)).isEqualTo(CategoryDeleteResult.DELETED)
        assertThat(repository.getBudgets(202611)).isEmpty()
    }

    @Test
    fun deleteAllDataKeepsCategories() = runBlocking {
        repository.addTransaction(draft())
        repository.setBudget(202611, 0L, 100_000L)

        repository.deleteAllData()

        assertThat(repository.getAllTransactions()).isEmpty()
        assertThat(repository.getBudgets(202611)).isEmpty()
        assertThat(repository.getCategories()).hasSize(categories.size)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
