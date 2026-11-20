package com.personalexpenses.tracker.util

import com.personalexpenses.tracker.data.model.CategorySpend
import com.personalexpenses.tracker.data.model.PeriodTotals
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import java.util.Locale

/** One row of the category breakdown on the reports screen. */
data class CategorySlice(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val totalCents: Long,
    val transactionCount: Int
) {
    fun shareOf(totalCents: Long): Float =
        if (totalCents <= 0L) 0f else (this.totalCents.toDouble() / totalCents).toFloat()

    fun sharePercentOf(totalCents: Long): Int =
        if (totalCents <= 0L) 0 else ((this.totalCents * 100) / totalCents).toInt()
}

/** A single bar of the trend chart. */
data class TrendPoint(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long
)

/**
 * Pure reporting calculations. Everything here is deterministic and free of
 * Android dependencies, which keeps the numbers easy to unit test.
 */
object ReportCalculator {

    fun summarize(rows: List<TransactionRow>): PeriodTotals {
        var income = 0L
        var expense = 0L
        for (row in rows) {
            if (row.type == TransactionType.INCOME) income += row.amountCents
            else expense += row.amountCents
        }
        return PeriodTotals(incomeCents = income, expenseCents = expense)
    }

    /**
     * Groups rows of [type] by category and returns them sorted from the
     * largest amount to the smallest. Ties are broken by name for stability.
     */
    fun categoryBreakdown(rows: List<TransactionRow>, type: TransactionType): List<CategorySlice> =
        rows.asSequence()
            .filter { it.type == type }
            .groupBy { it.categoryId }
            .map { (categoryId, group) ->
                val first = group.first()
                CategorySlice(
                    categoryId = categoryId,
                    name = first.categoryName,
                    iconKey = first.categoryIcon,
                    colorHex = first.categoryColor,
                    totalCents = group.sumOf { it.amountCents },
                    transactionCount = group.size
                )
            }
            .sortedWith(compareByDescending<CategorySlice> { it.totalCents }.thenBy { it.name })
            .toList()

    /**
     * One point per calendar day of [yearMonth], keyed by day of month. Days
     * without transactions are present with zero values.
     */
    fun dailyTrend(rows: List<TransactionRow>, yearMonth: Int): List<TrendPoint> {
        val days = Dates.daysInMonth(yearMonth)
        val expenses = LongArray(days + 1)
        val incomes = LongArray(days + 1)
        for (row in rows) {
            if (Dates.monthKeyOf(row.date) != yearMonth) continue
            val day = Dates.dayOfMonth(row.date)
            if (day !in 1..days) continue
            if (row.type == TransactionType.EXPENSE) expenses[day] += row.amountCents
            else incomes[day] += row.amountCents
        }
        return (1..days).map { day ->
            TrendPoint(
                label = day.toString(),
                incomeCents = incomes[day],
                expenseCents = expenses[day]
            )
        }
    }

    /** One point per month of [year], used for the yearly trend chart. */
    fun monthlyTrend(rows: List<TransactionRow>, year: Int): List<TrendPoint> {
        val expenses = LongArray(13)
        val incomes = LongArray(13)
        for (row in rows) {
            val rowMonth = Dates.monthKeyOf(row.date)
            if (Dates.yearOf(rowMonth) != year) continue
            val month = Dates.monthOf(rowMonth)
            if (month !in 1..12) continue
            if (row.type == TransactionType.EXPENSE) expenses[month] += row.amountCents
            else incomes[month] += row.amountCents
        }
        return (1..12).map { month ->
            val name = Dates.monthName(year * 100 + month, Locale.getDefault())
            TrendPoint(
                label = name.take(3),
                incomeCents = incomes[month],
                expenseCents = expenses[month]
            )
        }
    }

    fun averageDailyExpense(expenseCents: Long, days: Int): Long =
        if (days <= 0) 0L else expenseCents / days

    fun biggestExpense(rows: List<TransactionRow>): TransactionRow? =
        rows.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .maxByOrNull { it.amountCents }

    fun topCategory(rows: List<TransactionRow>, type: TransactionType): CategorySlice? =
        categoryBreakdown(rows, type).firstOrNull()

    /** Percentage of [total] represented by [part], rounded down. */
    fun percent(part: Long, total: Long): Int =
        if (total <= 0L) 0 else ((part * 100) / total).toInt()

    /** Maps SQL aggregates to slices, looking up display data from the rows. */
    fun slicesFromSpends(rows: List<TransactionRow>, spends: List<CategorySpend>): List<CategorySlice> {
        val byCategory = rows.associateBy { it.categoryId }
        return spends.mapNotNull { spend ->
            val row = byCategory[spend.categoryId] ?: return@mapNotNull null
            CategorySlice(
                categoryId = spend.categoryId,
                name = row.categoryName,
                iconKey = row.categoryIcon,
                colorHex = row.categoryColor,
                totalCents = spend.totalCents,
                transactionCount = spend.transactionCount
            )
        }
    }
}
