package com.personalexpenses.tracker.data.model

/** Aggregated spend for one category, produced by SQL or by [com.personalexpenses.tracker.util.ReportCalculator]. */
data class CategorySpend(
    val categoryId: Long,
    val totalCents: Long,
    val transactionCount: Int
)
