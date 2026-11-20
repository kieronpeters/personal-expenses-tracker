package com.personalexpenses.tracker.data.model

/** Income and expense totals for a period. */
data class PeriodTotals(
    val incomeCents: Long,
    val expenseCents: Long
) {
    val netCents: Long get() = incomeCents - expenseCents

    /** Percentage of income that was not spent; null when there is no income. */
    val savingsRatePercent: Int?
        get() = if (incomeCents <= 0L) null else (((incomeCents - expenseCents) * 100) / incomeCents).toInt()

    companion object {
        val ZERO = PeriodTotals(0L, 0L)
    }
}
