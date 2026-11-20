package com.personalexpenses.tracker.data.model

/** The user supplied values of a transaction, before validation/persistence. */
data class TransactionDraft(
    val amountCents: Long,
    val type: TransactionType,
    val categoryId: Long,
    val date: Long,
    val note: String = ""
)
