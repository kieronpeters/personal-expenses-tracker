package com.personalexpenses.tracker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Currency
import java.util.Locale

class MoneyTest {

    @Test
    fun `parses plain decimal with two fraction digits`() {
        assertThat(Money.parseToCents("12.34", 2)).isEqualTo(1234L)
        assertThat(Money.parseToCents("0.05", 2)).isEqualTo(5L)
        assertThat(Money.parseToCents("7", 2)).isEqualTo(700L)
    }

    @Test
    fun `parses currency symbols and spaces`() {
        assertThat(Money.parseToCents("$ 1,234.56", 2)).isEqualTo(123456L)
        assertThat(Money.parseToCents("EUR 12,34", 2)).isEqualTo(1234L)
    }

    @Test
    fun `uses the last separator when both are present`() {
        assertThat(Money.parseToCents("1.234,56", 2)).isEqualTo(123456L)
        assertThat(Money.parseToCents("1,234.56", 2)).isEqualTo(123456L)
    }

    @Test
    fun `treats a single comma with three digits as grouping`() {
        assertThat(Money.parseToCents("1,234", 2)).isEqualTo(123400L)
        assertThat(Money.parseToCents("12,345", 2)).isEqualTo(1234500L)
    }

    @Test
    fun `treats a single comma with plausible decimals as decimal separator`() {
        assertThat(Money.parseToCents("12,5", 2)).isEqualTo(1250L)
    }

    @Test
    fun `rounds extra precision`() {
        assertThat(Money.parseToCents("1.239", 2)).isEqualTo(124L)
        assertThat(Money.parseToCents("1.235", 2)).isEqualTo(124L)
    }

    @Test
    fun `supports zero decimal currencies`() {
        assertThat(Money.parseToCents("1234", 0)).isEqualTo(1234L)
        assertThat(Money.parseToCents("1,234", 0)).isEqualTo(1234L)
    }

    @Test
    fun `rejects invalid input`() {
        assertThat(Money.parseToCents("", 2)).isNull()
        assertThat(Money.parseToCents("   ", 2)).isNull()
        assertThat(Money.parseToCents("abc", 2)).isNull()
        assertThat(Money.parseToCents("-5", 2)).isNull()
        assertThat(Money.parseToCents(".", 2)).isNull()
        assertThat(Money.parseToCents("1.2.3.4", 2)).isNull()
    }

    @Test
    fun `rejects values that overflow a long`() {
        assertThat(Money.parseToCents("99999999999999999999", 2)).isNull()
    }

    @Test
    fun `formats with the currency of the locale`() {
        val formatted = Money.format(1234L, Currency.getInstance("USD"), Locale.US)
        assertThat(formatted).isEqualTo("$12.34")
    }

    @Test
    fun `formats zero decimal currency without decimals`() {
        val formatted = Money.format(1500L, Currency.getInstance("JPY"), Locale.US)
        assertThat(formatted).isEqualTo("¥1,500")
    }

    @Test
    fun `input string round trips`() {
        assertThat(Money.toInputString(1234L, 2)).isEqualTo("12.34")
        assertThat(Money.toInputString(0L, 2)).isEqualTo("0.00")
        assertThat(Money.toInputString(1234L, 0)).isEqualTo("1234")
    }

    @Test
    fun `unknown currency falls back to USD`() {
        assertThat(Money.currencyFor("XYZ").currencyCode).isEqualTo("USD")
        assertThat(Money.currencyFor("EUR").currencyCode).isEqualTo("EUR")
    }
}
