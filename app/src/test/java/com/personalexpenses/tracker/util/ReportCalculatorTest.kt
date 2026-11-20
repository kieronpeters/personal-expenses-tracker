package com.personalexpenses.tracker.util

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.model.CategorySpend
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.testing.dateAt
import org.junit.Test

class ReportCalculatorTest {

    private val food = row(1, 1_500, TransactionType.EXPENSE, 10, "Food", dateAt(2026, 11, 2))
    private val food2 = row(2, 500, TransactionType.EXPENSE, 10, "Food", dateAt(2026, 11, 2))
    private val transport = row(3, 2_000, TransactionType.EXPENSE, 11, "Transport", dateAt(2026, 11, 3))
    private val salary = row(4, 10_000, TransactionType.INCOME, 12, "Salary", dateAt(2026, 11, 1))
    private val october = row(5, 9_999, TransactionType.EXPENSE, 10, "Food", dateAt(2026, 10, 31))

    private val rows = listOf(food, food2, transport, salary, october)
    private val novemberRows = rows.filter { Dates.monthKeyOf(it.date) == 202611 }

    @Test
    fun `summarizes income expenses and net`() {
        val totals = ReportCalculator.summarize(novemberRows)
        assertThat(totals.expenseCents).isEqualTo(4_000L)
        assertThat(totals.incomeCents).isEqualTo(10_000L)
        assertThat(totals.netCents).isEqualTo(6_000L)
        assertThat(totals.savingsRatePercent).isEqualTo(60)
    }

    @Test
    fun `net can be negative and savings rate is unknown without income`() {
        val expensesOnly = ReportCalculator.summarize(listOf(food, transport))
        assertThat(expensesOnly.netCents).isEqualTo(-3_500L)
        assertThat(expensesOnly.savingsRatePercent).isNull()
    }

    @Test
    fun `category breakdown groups and sorts by amount`() {
        val slices = ReportCalculator.categoryBreakdown(novemberRows, TransactionType.EXPENSE)
        assertThat(slices).hasSize(2)
        assertThat(slices[0].name).isEqualTo("Food")
        assertThat(slices[0].totalCents).isEqualTo(2_000L)
        assertThat(slices[0].transactionCount).isEqualTo(2)
        assertThat(slices[1].name).isEqualTo("Transport")
        assertThat(slices[1].totalCents).isEqualTo(2_000L)
        // Tie broken by name for deterministic output.
        assertThat(slices[0].name.compareTo(slices[1].name)).isLessThan(0)
    }

    @Test
    fun `income breakdown only includes income`() {
        val slices = ReportCalculator.categoryBreakdown(novemberRows, TransactionType.INCOME)
        assertThat(slices).hasSize(1)
        assertThat(slices[0].name).isEqualTo("Salary")
        assertThat(slices[0].totalCents).isEqualTo(10_000L)
    }

    @Test
    fun `daily trend has one entry per day of the month`() {
        val trend = ReportCalculator.dailyTrend(novemberRows, 202611)
        assertThat(trend).hasSize(30)
        assertThat(trend[0].label).isEqualTo("1")
        assertThat(trend[0].incomeCents).isEqualTo(10_000L)
        assertThat(trend[1].expenseCents).isEqualTo(2_000L) // two food transactions
        assertThat(trend[2].expenseCents).isEqualTo(2_000L)
        assertThat(trend[29].expenseCents).isEqualTo(0L)
        assertThat(trend.sumOf { it.expenseCents }).isEqualTo(4_000L)
    }

    @Test
    fun `monthly trend has twelve entries for a year`() {
        val trend = ReportCalculator.monthlyTrend(rows, 2026)
        assertThat(trend).hasSize(12)
        assertThat(trend[9].expenseCents).isEqualTo(9_999L) // October
        assertThat(trend[10].expenseCents).isEqualTo(4_000L) // November
        assertThat(trend[10].incomeCents).isEqualTo(10_000L)
        assertThat(trend[0].label).isNotEmpty()
    }

    @Test
    fun `averages and percentages`() {
        assertThat(ReportCalculator.averageDailyExpense(3_000L, 30)).isEqualTo(100L)
        assertThat(ReportCalculator.averageDailyExpense(3_000L, 0)).isEqualTo(0L)
        assertThat(ReportCalculator.percent(2_500L, 10_000L)).isEqualTo(25)
        assertThat(ReportCalculator.percent(1L, 0L)).isEqualTo(0)
    }

    @Test
    fun `finds the biggest expense and top category`() {
        assertThat(ReportCalculator.biggestExpense(novemberRows)?.id).isEqualTo(3L)
        assertThat(ReportCalculator.biggestExpense(listOf(salary))).isNull()
        assertThat(ReportCalculator.topCategory(novemberRows, TransactionType.EXPENSE)?.name)
            .isEqualTo("Food")
    }

    @Test
    fun `slices from spends keep display data from the rows`() {
        val spends = listOf(
            CategorySpend(categoryId = 10, totalCents = 2_000, transactionCount = 2),
            CategorySpend(categoryId = 11, totalCents = 2_000, transactionCount = 1)
        )
        val slices = ReportCalculator.slicesFromSpends(novemberRows, spends)
        assertThat(slices.map { it.name }).containsExactly("Food", "Transport")
        assertThat(slices.first { it.categoryId == 10L }.transactionCount).isEqualTo(2)
    }

    @Test
    fun `slice shares`() {
        val slice = CategorySlice(1, "Food", "🍔", "#EF4444", 2_500, 3)
        assertThat(slice.shareOf(10_000L)).isWithin(0.0001f).of(0.25f)
        assertThat(slice.sharePercentOf(10_000L)).isEqualTo(25)
        assertThat(slice.sharePercentOf(0L)).isEqualTo(0)
    }

    private fun row(
        id: Long,
        cents: Long,
        type: TransactionType,
        categoryId: Long,
        categoryName: String,
        date: Long,
        note: String = ""
    ) = TransactionRow(
        id = id,
        amountCents = cents,
        type = type,
        categoryId = categoryId,
        date = date,
        note = note,
        categoryName = categoryName,
        categoryColor = "#EF4444",
        categoryIcon = "🍔"
    )
}
