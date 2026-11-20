package com.personalexpenses.tracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar helpers. Dates are persisted as epoch milliseconds at the start of
 * the local day, and months are keyed as the integer `yyyyMM` (for example
 * 202611), which sorts correctly and is easy to read in the database.
 */
object Dates {

    fun calendar(millis: Long = System.currentTimeMillis()): Calendar {
        val calendar = GregorianCalendar()
        calendar.timeInMillis = millis
        return calendar
    }

    fun startOfDay(millis: Long): Long {
        val calendar = calendar(millis)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun isSameDay(first: Long, second: Long): Boolean = startOfDay(first) == startOfDay(second)

    fun monthKeyOf(millis: Long): Int {
        val calendar = calendar(millis)
        return calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1
    }

    fun currentMonthKey(nowMillis: Long = System.currentTimeMillis()): Int = monthKeyOf(nowMillis)

    fun yearOf(yearMonth: Int): Int = yearMonth / 100

    fun monthOf(yearMonth: Int): Int = yearMonth % 100

    fun startOfMonth(yearMonth: Int): Long {
        val calendar = GregorianCalendar(yearOf(yearMonth), monthOf(yearMonth) - 1, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun endOfMonthExclusive(yearMonth: Int): Long = startOfMonth(shiftMonth(yearMonth, 1))

    fun shiftMonth(yearMonth: Int, delta: Int): Int {
        val calendar = GregorianCalendar(yearOf(yearMonth), monthOf(yearMonth) - 1, 1)
        calendar.add(Calendar.MONTH, delta)
        return calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1
    }

    fun daysInMonth(yearMonth: Int): Int {
        val calendar = GregorianCalendar(yearOf(yearMonth), monthOf(yearMonth) - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun daysInYear(year: Int): Int = if (GregorianCalendar().isLeapYear(year)) 366 else 365

    fun startOfYear(year: Int): Long = GregorianCalendar(year, Calendar.JANUARY, 1, 0, 0, 0).apply {
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfYearExclusive(year: Int): Long = GregorianCalendar(year + 1, Calendar.JANUARY, 1, 0, 0, 0).apply {
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun yearOfMillis(millis: Long): Int = calendar(millis).get(Calendar.YEAR)

    /** e.g. "November 2026". */
    fun monthLabel(yearMonth: Int, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("MMMM yyyy", locale)
        return format.format(Date(startOfMonth(yearMonth)))
    }

    /** e.g. "Nov 2026". */
    fun shortMonthLabel(yearMonth: Int, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("MMM yyyy", locale)
        return format.format(Date(startOfMonth(yearMonth)))
    }

    /** e.g. "November". */
    fun monthName(yearMonth: Int, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("MMMM", locale)
        return format.format(Date(startOfMonth(yearMonth)))
    }

    /** e.g. "Nov 20, 2026". */
    fun dateLabel(millis: Long, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("MMM d, yyyy", locale)
        return format.format(Date(millis))
    }

    /** e.g. "Nov 20". */
    fun shortDateLabel(millis: Long, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("MMM d", locale)
        return format.format(Date(millis))
    }

    /** e.g. "Monday, November 20". */
    fun longDateLabel(millis: Long, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("EEEE, MMMM d", locale)
        return format.format(Date(millis))
    }

    /**
     * Human friendly day heading used to group the transaction list:
     * "Today", "Yesterday", or a formatted date.
     */
    fun dayHeader(
        millis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault()
    ): String = when {
        isSameDay(millis, nowMillis) -> "Today"
        isSameDay(millis, startOfDay(nowMillis) - DAY_MILLIS) -> "Yesterday"
        yearOfMillis(millis) == yearOfMillis(nowMillis) -> shortDateLabel(millis, locale)
        else -> dateLabel(millis, locale)
    }

    /** Short weekday label ("Mon") used by the trend chart. */
    fun weekdayShort(millis: Long, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("EEE", locale)
        return format.format(Date(millis))
    }

    fun dayOfMonth(millis: Long): Int = calendar(millis).get(Calendar.DAY_OF_MONTH)

    fun stableTimeZoneId(): String = TimeZone.getDefault().id

    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    /** Parses an ISO-8601-ish `yyyy-MM-dd` value, used by tests and exports. */
    fun parseIsoDate(text: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(text)?.time
    } catch (e: Exception) {
        null
    }
}
