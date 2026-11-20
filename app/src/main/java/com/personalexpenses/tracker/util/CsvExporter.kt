package com.personalexpenses.tracker.util

import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType

/**
 * Builds a CSV document from transactions. Amounts are written as machine
 * readable decimal strings with a dot separator, independent of locale.
 */
object CsvExporter {

    const val HEADER = "Date,Type,Category,Amount,Currency,Note"

    fun build(
        rows: List<TransactionRow>,
        currencyCode: String,
        fractionDigits: Int
    ): String {
        val builder = StringBuilder()
        builder.append(HEADER).append("\r\n")
        rows.forEach { row ->
            builder.append(quote(Dates.dateLabel(row.date))).append(',')
            builder.append(if (row.type == TransactionType.INCOME) "INCOME" else "EXPENSE")
                .append(',')
            builder.append(quote(row.categoryName)).append(',')
            builder.append(Money.toInputString(row.amountCents, fractionDigits)).append(',')
            builder.append(currencyCode).append(',')
            builder.append(quote(row.note)).append("\r\n")
        }
        return builder.toString()
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
