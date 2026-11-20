package com.personalexpenses.tracker.data.model

/**
 * A budget for a category (or the overall monthly budget when
 * [categoryId] is [OVERALL_CATEGORY_ID]) compared against actual spending.
 */
data class BudgetProgress(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val budgetCents: Long,
    val spentCents: Long
) {
    val isOverall: Boolean get() = categoryId == OVERALL_CATEGORY_ID

    val remainingCents: Long get() = budgetCents - spentCents

    val isOverBudget: Boolean get() = spentCents > budgetCents

    /** Progress clamped to 0..1 for progress bars. */
    val usedFraction: Float
        get() = if (budgetCents <= 0L) 1f else (spentCents.toDouble() / budgetCents).coerceIn(0.0, 1.0).toFloat()

    /** Percentage of the budget that has been used; may exceed 100. */
    val usedPercent: Int
        get() = if (budgetCents <= 0L) 0 else ((spentCents * 100) / budgetCents).toInt()

    companion object {
        /** Sentinel category id used for the overall monthly budget. */
        const val OVERALL_CATEGORY_ID = 0L
    }
}
