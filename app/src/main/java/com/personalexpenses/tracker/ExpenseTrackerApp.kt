package com.personalexpenses.tracker

import android.app.Application
import android.content.Context
import com.personalexpenses.tracker.data.AppDatabase
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.ExpenseRepository
import com.personalexpenses.tracker.util.Prefs

/**
 * Application entry point. Owns the database, repository and settings that are
 * shared by every screen.
 */
class ExpenseTrackerApp : Application() {

    lateinit var prefs: Prefs
        private set

    lateinit var repository: ExpenseDataSource
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val database = AppDatabase.getInstance(this)
        repository = ExpenseRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            budgetDao = database.budgetDao()
        )
    }

    companion object {
        fun from(context: Context): ExpenseTrackerApp = context.applicationContext as ExpenseTrackerApp
    }
}
