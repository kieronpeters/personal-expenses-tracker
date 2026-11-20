package com.personalexpenses.tracker.util

import android.content.Context
import android.content.SharedPreferences
import java.util.Currency
import java.util.Locale

/**
 * Small typed wrapper around [SharedPreferences] for user settings. The
 * currency defaults to the device locale's currency.
 */
class Prefs(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var currencyCode: String
        get() = preferences.getString(KEY_CURRENCY, null) ?: defaultCurrencyCode()
        set(value) {
            preferences.edit().putString(KEY_CURRENCY, value).apply()
        }

    fun currency(): Currency = Money.currencyFor(currencyCode)

    /** Number of decimal digits used by the selected currency (2 for most). */
    fun fractionDigits(): Int = currency().defaultFractionDigits.coerceIn(0, 3)

    fun formatted(cents: Long): String = Money.format(cents, currency())

    fun formattedPlain(cents: Long): String = Money.formatPlain(cents, fractionDigits())

    fun currencySymbol(): String = Money.symbol(currency())

    companion object {
        const val FILE_NAME = "personal_expenses_settings"
        const val KEY_CURRENCY = "currency_code"

        val COMMON_CURRENCIES: List<String> = listOf(
            "USD", "EUR", "GBP", "JPY", "CNY", "INR", "AUD", "CAD", "CHF", "SGD",
            "HKD", "NZD", "SEK", "NOK", "DKK", "PLN", "BRL", "MXN", "ZAR", "KRW",
            "AED", "SAR", "RUB", "TRY", "IDR", "MYR", "PHP", "THB", "VND", "NGN"
        )

        fun defaultCurrencyCode(): String = try {
            Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (e: IllegalArgumentException) {
            "USD"
        }
    }
}
