package com.personalexpenses.tracker.data

import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.TransactionType

/**
 * The categories every fresh install starts with. They are inserted when the
 * database is created, and are also used by tests.
 */
object SeedData {

    const val DEFAULT_ICON = "\uD83C\uDFF7\uFE0F" // label emoji, used for custom categories

    fun defaultCategories(): List<CategoryEntity> = listOf(
        // Expense categories
        category("Food & Dining", TransactionType.EXPENSE, "#EF4444", "\uD83C\uDF54", 0),
        category("Groceries", TransactionType.EXPENSE, "#F97316", "\uD83D\uDED2", 1),
        category("Transport", TransactionType.EXPENSE, "#3B82F6", "\uD83D\uDE8C", 2),
        category("Housing", TransactionType.EXPENSE, "#8B5CF6", "\uD83C\uDFE0", 3),
        category("Utilities", TransactionType.EXPENSE, "#06B6D4", "\uD83D\uDCA1", 4),
        category("Shopping", TransactionType.EXPENSE, "#EC4899", "\uD83D\uDECD\uFE0F", 5),
        category("Health", TransactionType.EXPENSE, "#10B981", "\uD83D\uDC8A", 6),
        category("Entertainment", TransactionType.EXPENSE, "#F59E0B", "\uD83C\uDFAC", 7),
        category("Education", TransactionType.EXPENSE, "#6366F1", "\uD83D\uDCDA", 8),
        category("Travel", TransactionType.EXPENSE, "#14B8A6", "\u2708\uFE0F", 9),
        category("Other", TransactionType.EXPENSE, "#6B7280", "\u2795", 10),
        // Income categories
        category("Salary", TransactionType.INCOME, "#059669", "\uD83D\uDCB0", 0),
        category("Freelance", TransactionType.INCOME, "#0EA5E9", "\uD83D\uDCBB", 1),
        category("Investments", TransactionType.INCOME, "#84CC16", "\uD83D\uDCC8", 2),
        category("Gifts", TransactionType.INCOME, "#F43F5E", "\uD83C\uDF81", 3),
        category("Other Income", TransactionType.INCOME, "#64748B", "\uD83E\uDE99", 4)
    )

    private fun category(
        name: String,
        type: TransactionType,
        colorHex: String,
        iconKey: String,
        sortOrder: Int
    ) = CategoryEntity(
        name = name,
        type = type,
        colorHex = colorHex,
        iconKey = iconKey,
        isDefault = true,
        sortOrder = sortOrder
    )
}
