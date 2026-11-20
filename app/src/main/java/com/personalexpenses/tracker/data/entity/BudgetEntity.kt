package com.personalexpenses.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly budget. [categoryId] is [com.personalexpenses.tracker.data.model.BudgetProgress.OVERALL_CATEGORY_ID]
 * (0) for the overall monthly budget, otherwise a category id. A unique index
 * keeps one budget per category per month.
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["yearMonth", "categoryId"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Month key in `yyyyMM` form, for example 202611. */
    val yearMonth: Int,
    /** Category id, or 0 for the overall monthly budget. */
    val categoryId: Long,
    val amountCents: Long
)
