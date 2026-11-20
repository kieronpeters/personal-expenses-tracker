package com.personalexpenses.tracker.util

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.testing.dateAt
import org.junit.Test

class DatesTest {

    @Test
    fun `month key encodes year and month`() {
        assertThat(Dates.monthKeyOf(dateAt(2026, 11, 20))).isEqualTo(202611)
        assertThat(Dates.monthKeyOf(dateAt(2026, 1, 1))).isEqualTo(202601)
        assertThat(Dates.monthKeyOf(dateAt(2025, 12, 31))).isEqualTo(202512)
    }

    @Test
    fun `start of month is at midnight of the first day`() {
        val start = Dates.startOfMonth(202611)
        assertThat(start).isEqualTo(dateAt(2026, 11, 1))
    }

    @Test
    fun `end of month is exclusive`() {
        assertThat(Dates.endOfMonthExclusive(202611)).isEqualTo(dateAt(2026, 12, 1))
        assertThat(Dates.endOfMonthExclusive(202602)).isEqualTo(dateAt(2026, 3, 1))
    }

    @Test
    fun `shift month crosses year boundaries`() {
        assertThat(Dates.shiftMonth(202601, -1)).isEqualTo(202512)
        assertThat(Dates.shiftMonth(202512, 1)).isEqualTo(202601)
        assertThat(Dates.shiftMonth(202611, 3)).isEqualTo(202702)
        assertThat(Dates.shiftMonth(202603, -14)).isEqualTo(202501)
    }

    @Test
    fun `days in month handles leap years`() {
        assertThat(Dates.daysInMonth(202402)).isEqualTo(29)
        assertThat(Dates.daysInMonth(202302)).isEqualTo(28)
        assertThat(Dates.daysInMonth(202611)).isEqualTo(30)
        assertThat(Dates.daysInMonth(202612)).isEqualTo(31)
    }

    @Test
    fun `days in year handles leap years`() {
        assertThat(Dates.daysInYear(2024)).isEqualTo(366)
        assertThat(Dates.daysInYear(2026)).isEqualTo(365)
    }

    @Test
    fun `month labels are localized`() {
        assertThat(Dates.monthLabel(202611, java.util.Locale.US)).isEqualTo("November 2026")
        assertThat(Dates.shortMonthLabel(202611, java.util.Locale.US)).isEqualTo("Nov 2026")
        assertThat(Dates.monthName(202602, java.util.Locale.US)).isEqualTo("February")
    }

    @Test
    fun `day header uses today and yesterday names`() {
        val now = dateAt(2026, 11, 20)
        assertThat(Dates.dayHeader(now, now)).isEqualTo("Today")
        assertThat(Dates.dayHeader(dateAt(2026, 11, 19), now)).isEqualTo("Yesterday")
        assertThat(Dates.dayHeader(dateAt(2026, 11, 15), now)).isEqualTo("Nov 15")
        assertThat(Dates.dayHeader(dateAt(2025, 11, 15), now)).isEqualTo("Nov 15, 2025")
    }

    @Test
    fun `start of day strips the time component`() {
        val withTime = dateAt(2026, 11, 20) + 1000L * 60 * 60 * 13
        assertThat(Dates.startOfDay(withTime)).isEqualTo(dateAt(2026, 11, 20))
    }

    @Test
    fun `month key and date conversions round trip`() {
        val month = Dates.monthKeyOf(dateAt(2026, 7, 4))
        assertThat(Dates.yearOf(month)).isEqualTo(2026)
        assertThat(Dates.monthOf(month)).isEqualTo(7)
        assertThat(Dates.startOfYear(2026)).isEqualTo(dateAt(2026, 1, 1))
        assertThat(Dates.endOfYearExclusive(2026)).isEqualTo(dateAt(2027, 1, 1))
    }
}
