package com.personalexpenses.tracker.data.model

/**
 * Immutable description of a transaction query. The date range is half open:
 * `[startInclusive, endExclusive)`.
 */
data class TransactionFilter(
    val startInclusive: Long,
    val endExclusive: Long,
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val query: String = ""
) {
    fun withType(type: TransactionType?): TransactionFilter = copy(type = type)
    fun withQuery(query: String): TransactionFilter = copy(query = query)
}
