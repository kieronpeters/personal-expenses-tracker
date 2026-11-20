package com.personalexpenses.tracker.data.model

/** Whether a transaction moves money out of (expense) or into (income) the budget. */
enum class TransactionType {
    EXPENSE,
    INCOME;

    val isIncome: Boolean get() = this == INCOME

    companion object {
        fun fromName(value: String?): TransactionType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
