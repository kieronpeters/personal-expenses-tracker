package com.personalexpenses.tracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Helpers for converting between user-entered amount text and integer minor
 * units ("cents"). All monetary values in the app are stored as [Long] minor
 * units so that no floating point rounding error can ever reach the database.
 */
object Money {

    /**
     * Parses user input into minor units.
     *
     * Accepts grouping separators, decimal commas/dots, and currency symbols.
     * Returns null when the text is not a valid, non-negative amount.
     *
     * @param fractionDigits number of decimal digits for the active currency
     *     (usually 2, 0 for JPY, 3 for KWD).
     */
    fun parseToCents(input: String, fractionDigits: Int = 2): Long? {
        if (fractionDigits < 0 || fractionDigits > 6) return null
        val cleaned = input.trim().filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (cleaned.isEmpty()) return null
        if (cleaned.contains('-')) return null // amounts are unsigned; the type carries direction
        if (cleaned.none { it.isDigit() }) return null

        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val decimalSeparator: Char? = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 -> resolveSingleSeparator(cleaned, '.', fractionDigits)
            lastComma >= 0 -> resolveSingleSeparator(cleaned, ',', fractionDigits)
            else -> null
        }

        val integerPart: String
        val fractionPart: String
        var integerRaw: String
        if (decimalSeparator == null) {
            integerRaw = cleaned
            integerPart = cleaned.filter { it.isDigit() }
            fractionPart = ""
        } else {
            val index = cleaned.lastIndexOf(decimalSeparator)
            integerRaw = cleaned.substring(0, index)
            integerPart = integerRaw.filter { it.isDigit() }
            fractionPart = cleaned.substring(index + 1).filter { it.isDigit() }
        }
        if (integerPart.isEmpty() && fractionPart.isEmpty()) return null

        // Reject malformed grouping such as "1.2.3.4" instead of silently
        // accepting it as 1234.
        val groupingSeparators = integerRaw.filter { !it.isDigit() }
        if (groupingSeparators.isNotEmpty()) {
            val separator = groupingSeparators.first()
            if (groupingSeparators.any { it != separator }) return null
            val groups = integerRaw.split(separator)
            if (groups.isEmpty() || groups.first().isEmpty() || groups.first().length > 3) return null
            if (groups.drop(1).any { it.length != 3 }) return null
            if (groups.any { group -> group.isEmpty() || group.any { !it.isDigit() } }) return null
        }

        return try {
            val whole = if (integerPart.isEmpty()) BigDecimal.ZERO else BigDecimal(integerPart)
            val fraction = if (fractionPart.isEmpty()) BigDecimal.ZERO else BigDecimal("0.$fractionPart")
            whole.add(fraction)
                .multiply(BigDecimal.TEN.pow(fractionDigits))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Decides whether the single separator in [text] is a decimal separator or
     * a thousands grouping separator. A dot is always treated as the decimal
     * separator (the amount field only offers a dot key), while a single comma
     * is only a decimal separator when it is followed by a plausible number of
     * decimal digits; otherwise "1,234" is read as one thousand two hundred
     * thirty four.
     */
    private fun resolveSingleSeparator(text: String, separator: Char, fractionDigits: Int): Char? {
        val occurrences = text.count { it == separator }
        if (occurrences > 1) return null // repeated: grouping only
        if (separator == '.') return '.'
        val digitsAfter = text.length - text.lastIndexOf(separator) - 1
        return if (digitsAfter in 1..fractionDigits) separator else null
    }

    /** Formats minor units as a localized currency string, e.g. "$12.34". */
    fun format(cents: Long, currency: Currency, locale: Locale = Locale.getDefault()): String {
        val digits = currency.defaultFractionDigits.coerceIn(0, 6)
        val amount = BigDecimal(cents).movePointLeft(digits)
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(amount)
    }

    /** Formats minor units as localized decimal text without the currency symbol. */
    fun formatPlain(cents: Long, fractionDigits: Int = 2, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits.coerceIn(0, 6)
        val amount = BigDecimal(cents).movePointLeft(digits)
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(amount)
    }

    /** Machine readable decimal string, suitable for pre-filling an input field. */
    fun toInputString(cents: Long, fractionDigits: Int = 2): String {
        val digits = fractionDigits.coerceIn(0, 6)
        return BigDecimal(cents).movePointLeft(digits).setScale(digits, RoundingMode.UNNECESSARY).toPlainString()
    }

    /** Returns the symbol of the given currency, falling back to the code. */
    fun symbol(currency: Currency, locale: Locale = Locale.getDefault()): String {
        val symbol = currency.getSymbol(locale)
        return if (symbol.isBlank()) currency.currencyCode else symbol
    }

    /** Returns the currency used for the given ISO-4217 code, or USD if unknown. */
    fun currencyFor(code: String): Currency = try {
        Currency.getInstance(code)
    } catch (e: IllegalArgumentException) {
        Currency.getInstance("USD")
    }
}
