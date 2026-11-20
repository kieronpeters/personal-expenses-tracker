package com.personalexpenses.tracker.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Replaces the main dispatcher with a test dispatcher for view model tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** Builds a local timezone timestamp at midnight for the given calendar day. */
fun dateAt(year: Int, month: Int, day: Int): Long =
    GregorianCalendar(year, month - 1, day, 0, 0, 0).apply {
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Builds a local timezone timestamp with an explicit hour. */
fun dateTimeAt(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
    GregorianCalendar(year, month - 1, day, hour, minute, 0).apply {
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** The test JVM's default timezone; exposed so tests can document assumptions. */
val testTimeZone: TimeZone get() = TimeZone.getDefault()
