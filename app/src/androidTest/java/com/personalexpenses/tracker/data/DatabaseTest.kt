package com.personalexpenses.tracker.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.dao.BudgetDao
import com.personalexpenses.tracker.data.dao.CategoryDao
import com.personalexpenses.tracker.data.dao.TransactionDao
import com.personalexpenses.tracker.data.entity.BudgetEntity
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.entity.TransactionEntity
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.util.Dates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the Room schema: joins, filters, aggregates, the
 * budget uniqueness constraint and foreign key enforcement.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var budgetDao: BudgetDao

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
        budgetDao = database.budgetDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun seedCategories(): List<CategoryEntity> = runBlocking {
        SeedData.defaultCategories().map { category ->
            val id = categoryDao.insert(category.copy(id = 0L))
            categoryDao.findById(id)!!
        }
    }

    private fun categoryId(categories: List<CategoryEntity>, name: String): Long =
        categories.first { it.name == name }.id

    @Test
    fun defaultCategoriesAreSeededWithUniqueNamesAndTypes() = runBlocking {
        val categories = seedCategories()
        assertThat(categories).hasSize(16)
        assertThat(categories.map { it.name to it.type }.toSet()).hasSize(16)
        assertThat(categories.all { it.isDefault }).isTrue()
        assertThat(categories.count { it.type == TransactionType.EXPENSE }).isEqualTo(11)
        assertThat(categories.count { it.type == TransactionType.INCOME }).isEqualTo(5)
    }

    @Test
    fun transactionJoinReturnsCategoryDisplayData() = runBlocking {
        val categories = seedCategories()
        val foodId = categoryId(categories, "Food & Dining")
        val date = Dates.startOfDay(System.currentTimeMillis())
        val id = transactionDao.insert(
            TransactionEntity(
                amountCents = 1_234,
                type = TransactionType.EXPENSE,
                categoryId = foodId,
                date = date,
                note = "Lunch"
            )
        )

        val row = transactionDao.findRowById(id)
        assertThat(row).isNotNull()
        assertThat(row!!.amountCents).isEqualTo(1_234L)
        assertThat(row.categoryName).isEqualTo("Food & Dining")
        assertThat(row.categoryIcon).isNotEmpty()
        assertThat(row.categoryColor).startsWith("#")
    }

    @Test
    fun filteredQueriesApplyTypeCategoryAndText() = runBlocking {
        val categories = seedCategories()
        val foodId = categoryId(categories, "Food & Dining")
        val salaryId = categoryId(categories, "Salary")
        val transportId = categoryId(categories, "Transport")
        val start = Dates.startOfMonth(202611)
        val end = Dates.endOfMonthExclusive(202611)

        transactionDao.insert(
            TransactionEntity(
                amountCents = 1_000,
                type = TransactionType.EXPENSE,
                categoryId = foodId,
                date = Dates.startOfMonth(202611) + 1,
                note = "Team lunch"
            )
        )
        transactionDao.insert(
            TransactionEntity(
                amountCents = 2_000,
                type = TransactionType.EXPENSE,
                categoryId = transportId,
                date = Dates.startOfMonth(202611) + 2,
                note = "Bus pass"
            )
        )
        transactionDao.insert(
            TransactionEntity(
                amountCents = 5_000,
                type = TransactionType.INCOME,
                categoryId = salaryId,
                date = Dates.startOfMonth(202611) + 3,
                note = "Salary"
            )
        )

        val all = transactionDao.findFiltered(start, end, null, null, "")
        assertThat(all).hasSize(3)

        val expenses = transactionDao.findFiltered(start, end, TransactionType.EXPENSE, null, "")
        assertThat(expenses).hasSize(2)
        assertThat(expenses.all { it.type == TransactionType.EXPENSE }).isTrue()

        val byCategory = transactionDao.findFiltered(start, end, null, transportId, "")
        assertThat(byCategory).hasSize(1)
        assertThat(byCategory.first().categoryName).isEqualTo("Transport")

        val byText = transactionDao.findFiltered(start, end, null, null, "lunch")
        assertThat(byText).hasSize(1)
        assertThat(byText.first().note).isEqualTo("Team lunch")

        val observed = transactionDao.observeFiltered(start, end, TransactionType.INCOME, null, "")
            .first()
        assertThat(observed).hasSize(1)
        assertThat(observed.first().categoryName).isEqualTo("Salary")
    }

    @Test
    fun sumsAndCategoryTotalsAreCalculatedInSql() = runBlocking {
        val categories = seedCategories()
        val foodId = categoryId(categories, "Food & Dining")
        val transportId = categoryId(categories, "Transport")
        val salaryId = categoryId(categories, "Salary")
        val start = Dates.startOfMonth(202611)
        val end = Dates.endOfMonthExclusive(202611)

        listOf(
            Triple(1_000L, foodId, TransactionType.EXPENSE),
            Triple(2_500L, foodId, TransactionType.EXPENSE),
            Triple(4_000L, transportId, TransactionType.EXPENSE),
            Triple(9_000L, salaryId, TransactionType.INCOME)
        ).forEachIndexed { index, (amount, categoryId, type) ->
            transactionDao.insert(
                TransactionEntity(
                    amountCents = amount,
                    type = type,
                    categoryId = categoryId,
                    date = start + index + 1
                )
            )
        }

        assertThat(transactionDao.sumByType(TransactionType.EXPENSE, start, end))
            .isEqualTo(7_500L)
        assertThat(transactionDao.sumByType(TransactionType.INCOME, start, end))
            .isEqualTo(9_000L)

        val totals = transactionDao.categoryTotals(TransactionType.EXPENSE, start, end)
        assertThat(totals).hasSize(2)
        assertThat(totals.first().categoryId).isEqualTo(transportId)
        assertThat(totals.first().totalCents).isEqualTo(4_000L)
        val foodTotal = totals.first { it.categoryId == foodId }
        assertThat(foodTotal.totalCents).isEqualTo(3_500L)
        assertThat(foodTotal.transactionCount).isEqualTo(2)
    }

    @Test
    fun budgetIsUniquePerMonthAndCategory() = runBlocking {
        budgetDao.upsert(
            BudgetEntity(yearMonth = 202611, categoryId = 5L, amountCents = 10_000L)
        )
        budgetDao.upsert(
            BudgetEntity(yearMonth = 202611, categoryId = 5L, amountCents = 20_000L)
        )
        budgetDao.upsert(
            BudgetEntity(yearMonth = 202611, categoryId = 0L, amountCents = 50_000L)
        )
        budgetDao.upsert(
            BudgetEntity(yearMonth = 202612, categoryId = 5L, amountCents = 30_000L)
        )

        val november = budgetDao.getForMonth(202611)
        assertThat(november).hasSize(2)
        assertThat(november.first { it.categoryId == 5L }.amountCents).isEqualTo(20_000L)

        val december = budgetDao.getForMonth(202612)
        assertThat(december).hasSize(1)
    }

    @Test
    fun foreignKeyPreventsDeletingCategoryInUse() = runBlocking {
        val categories = seedCategories()
        val foodId = categoryId(categories, "Food & Dining")
        transactionDao.insert(
            TransactionEntity(
                amountCents = 500,
                type = TransactionType.EXPENSE,
                categoryId = foodId,
                date = Dates.startOfMonth(202611)
            )
        )

        val food = categories.first { it.id == foodId }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { categoryDao.delete(food) }
        }
        assertThat(categoryDao.findById(foodId)).isNotNull()
    }
}
