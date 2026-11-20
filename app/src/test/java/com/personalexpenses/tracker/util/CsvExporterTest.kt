package com.personalexpenses.tracker.util

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.testing.dateAt
import org.junit.Test

class CsvExporterTest {

    @Test
    fun `writes header and one line per transaction`() {
        val rows = listOf(
            row(1, 1_250, TransactionType.EXPENSE, "Food & Dining", "Lunch", dateAt(2026, 11, 2)),
            row(2, 500_000, TransactionType.INCOME, "Salary", "Monthly pay", dateAt(2026, 11, 1))
        )
        val csv = CsvExporter.build(rows, "USD", 2)
        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).isEqualTo("Date,Type,Category,Amount,Currency,Note")
        assertThat(lines[1]).contains("EXPENSE")
        assertThat(lines[1]).contains("\"Food & Dining\"")
        assertThat(lines[1]).contains(",12.50,USD,")
        assertThat(lines[2]).contains("INCOME")
        assertThat(lines[2]).contains(",5000.00,USD,")
    }

    @Test
    fun `escapes quotes and commas inside fields`() {
        val row = row(
            1,
            100,
            TransactionType.EXPENSE,
            "Food, Drinks",
            "He said \"hi\", twice",
            dateAt(2026, 11, 2)
        )
        val csv = CsvExporter.build(listOf(row), "EUR", 2)
        assertThat(csv).contains("\"Food, Drinks\"")
        assertThat(csv).contains("\"He said \"\"hi\"\", twice\"")
        assertThat(csv).contains(",1.00,EUR,")
    }

    @Test
    fun `empty export still contains the header`() {
        val csv = CsvExporter.build(emptyList(), "USD", 2)
        assertThat(csv).isEqualTo("Date,Type,Category,Amount,Currency,Note\r\n")
    }

    private fun row(
        id: Long,
        cents: Long,
        type: TransactionType,
        category: String,
        note: String,
        date: Long
    ) = TransactionRow(
        id = id,
        amountCents = cents,
        type = type,
        categoryId = 1L,
        date = date,
        note = note,
        categoryName = category,
        categoryColor = "#EF4444",
        categoryIcon = "🍔"
    )
}
