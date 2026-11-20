package com.personalexpenses.tracker.data.model

/**
 * Flattened transaction joined with its category, which is what every screen
 * needs to render a row without additional lookups.
 */
data class TransactionRow(
    val id: Long,
    val amountCents: Long,
    val type: TransactionType,
    val categoryId: Long,
    val date: Long,
    val note: String,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String
)
