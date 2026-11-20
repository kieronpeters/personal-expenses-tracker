package com.personalexpenses.tracker

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking

/** Polling helpers for asynchronous Room/Flow driven UI updates. */
object UiWait {

    fun until(timeoutMs: Long = 6_000L, condition: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastFailure: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                condition()
                return
            } catch (t: Throwable) {
                lastFailure = t
                Thread.sleep(50)
            }
        }
        throw AssertionError("Condition was not satisfied within ${timeoutMs}ms", lastFailure)
    }
}

/** Clears all transactions and budgets, and pins the display currency. */
fun resetUserData() {
    val app = ApplicationProvider.getApplicationContext<ExpenseTrackerApp>()
    runBlocking { app.repository.deleteAllData() }
    app.prefs.currencyCode = "USD"
}

fun app(): ExpenseTrackerApp = ApplicationProvider.getApplicationContext()
