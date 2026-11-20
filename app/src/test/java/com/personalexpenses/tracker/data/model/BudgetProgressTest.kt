package com.personalexpenses.tracker.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetProgressTest {

    @Test
    fun `calculates remaining and fraction`() {
        val progress = BudgetProgress(
            categoryId = 1L,
            categoryName = "Food",
            categoryColor = "#EF4444",
            categoryIcon = "🍔",
            budgetCents = 10_000L,
            spentCents = 2_500L
        )
        assertThat(progress.remainingCents).isEqualTo(7_500L)
        assertThat(progress.usedFraction).isWithin(0.0001f).of(0.25f)
        assertThat(progress.usedPercent).isEqualTo(25)
        assertThat(progress.isOverBudget).isFalse()
    }

    @Test
    fun `clamps the progress bar but keeps the real percentage`() {
        val progress = BudgetProgress(
            categoryId = 1L,
            categoryName = "Food",
            categoryColor = "#EF4444",
            categoryIcon = "🍔",
            budgetCents = 10_000L,
            spentCents = 15_000L
        )
        assertThat(progress.usedFraction).isWithin(0.0001f).of(1f)
        assertThat(progress.usedPercent).isEqualTo(150)
        assertThat(progress.isOverBudget).isTrue()
        assertThat(progress.remainingCents).isEqualTo(-5_000L)
    }

    @Test
    fun `zero or negative budget never divides by zero`() {
        val progress = BudgetProgress(
            categoryId = 1L,
            categoryName = "Food",
            categoryColor = "#EF4444",
            categoryIcon = "🍔",
            budgetCents = 0L,
            spentCents = 1_000L
        )
        assertThat(progress.usedFraction).isWithin(0.0001f).of(1f)
        assertThat(progress.usedPercent).isEqualTo(0)
        assertThat(progress.isOverBudget).isTrue()
    }

    @Test
    fun `overall budget uses sentinel id`() {
        val overall = BudgetProgress(
            categoryId = BudgetProgress.OVERALL_CATEGORY_ID,
            categoryName = "Overall",
            categoryColor = "#0F766E",
            categoryIcon = "💰",
            budgetCents = 100_000L,
            spentCents = 100_000L
        )
        assertThat(overall.isOverall).isTrue()
        assertThat(overall.isOverBudget).isFalse()
        assertThat(overall.usedPercent).isEqualTo(100)
        assertThat(overall.remainingCents).isEqualTo(0L)
    }
}
