package com.personalexpenses.tracker.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.ExpenseTrackerApp
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.UiWait
import com.personalexpenses.tracker.app
import com.personalexpenses.tracker.data.model.TransactionDraft
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.resetUserData
import com.personalexpenses.tracker.util.Dates
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI tests for adding, editing, deleting and undoing transactions
 * through the real activity, database and view models.
 */
@RunWith(AndroidJUnit4::class)
class TransactionFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        resetUserData()
    }

    @Test
    fun addingExpenseUpdatesListAndTotalsAndPersists() {
        openAddScreen()
        onView(withId(R.id.et_amount)).perform(replaceText("12.50"))
        onView(withId(R.id.btn_save)).perform(click())

        UiWait.until {
            onView(
                allOf(withId(R.id.tv_category_name), withText("Food & Dining"))
            ).check(matches(isDisplayed()))
        }
        onView(withId(R.id.tv_spent)).check(matches(withText(containsString("12.50"))))
        onView(withId(R.id.tv_balance)).check(matches(withText(containsString("12.50"))))

        val rows = runBlocking { app().repository.getAllTransactions() }
        assertThat(rows).hasSize(1)
        assertThat(rows.first().amountCents).isEqualTo(1_250L)
        assertThat(rows.first().type).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun saveWithoutAmountShowsValidationError() {
        openAddScreen()
        onView(withId(R.id.btn_save)).perform(click())

        onView(withText(R.string.error_invalid_amount)).check(matches(isDisplayed()))
        assertThat(runBlocking { app().repository.countTransactions() }).isEqualTo(0)
    }

    @Test
    fun editingTransactionUpdatesTheStoredAmount() {
        seedExpense(1_250L)
        UiWait.until {
            onView(withId(R.id.row_transaction)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.row_transaction)).perform(click())
        UiWait.until {
            onView(withId(R.id.et_amount)).check(matches(withText("12.50")))
        }
        onView(withId(R.id.et_amount)).perform(replaceText("20.00"))
        onView(withId(R.id.btn_save)).perform(click())

        UiWait.until {
            onView(withId(R.id.tv_amount)).check(matches(withText(containsString("20.00"))))
        }
        val rows = runBlocking { app().repository.getAllTransactions() }
        assertThat(rows.single().amountCents).isEqualTo(2_000L)
    }

    @Test
    fun deletingFromTheEditorRemovesTheTransaction() {
        seedExpense(1_250L)
        UiWait.until {
            onView(withId(R.id.row_transaction)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.row_transaction)).perform(click())
        UiWait.until {
            onView(withId(R.id.btn_delete)).check(matches(isDisplayed()))
        }
        onView(withId(R.id.btn_delete)).perform(click())
        onView(withText(R.string.confirm_delete)).perform(click())

        UiWait.until {
            onView(withId(R.id.empty_state)).check(matches(isDisplayed()))
        }
        assertThat(runBlocking { app().repository.countTransactions() }).isEqualTo(0)
    }

    @Test
    fun swipeToDeleteCanBeUndone() {
        seedExpense(1_250L)
        UiWait.until {
            onView(withId(R.id.row_transaction)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.row_transaction)).perform(swipeLeft())
        UiWait.until {
            onView(withText(R.string.transaction_deleted)).check(matches(isDisplayed()))
        }
        assertThat(runBlocking { app().repository.countTransactions() }).isEqualTo(0)

        onView(withText(R.string.action_undo)).perform(click())
        UiWait.until {
            onView(withId(R.id.row_transaction)).check(matches(isDisplayed()))
        }
        assertThat(runBlocking { app().repository.countTransactions() }).isEqualTo(1)
    }

    @Test
    fun typeFilterAndSearchNarrowTheList() {
        seedExpense(1_250L)
        seedIncome(5_000L)
        UiWait.until {
            onView(allOf(withId(R.id.tv_category_name), withText("Salary")))
                .check(matches(isDisplayed()))
        }

        onView(withId(R.id.btn_filter_expense)).perform(click())
        UiWait.until {
            onView(withId(R.id.tv_category_name)).check(matches(withText("Food & Dining")))
        }
        onView(withId(R.id.tv_category_name)).check(matches(withText("Food & Dining")))

        onView(withId(R.id.btn_filter_all)).perform(click())
        UiWait.until {
            onView(allOf(withId(R.id.tv_category_name), withText("Salary")))
                .check(matches(isDisplayed()))
        }

        onView(withId(R.id.et_search)).perform(replaceText("Salary"))
        UiWait.until {
            onView(allOf(withId(R.id.tv_category_name), withText("Food & Dining")))
                .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        }
        onView(allOf(withId(R.id.tv_category_name), withText("Salary")))
            .check(matches(isDisplayed()))
    }

    private fun openAddScreen() {
        onView(withId(R.id.fab_add)).perform(click())
        UiWait.until {
            onView(withId(R.id.et_category)).check(
                matches(withText(containsString("Food & Dining")))
            )
        }
    }

    private fun seedExpense(cents: Long) {
        runBlocking {
            app().repository.addTransaction(
                TransactionDraft(
                    amountCents = cents,
                    type = TransactionType.EXPENSE,
                    categoryId = foodCategoryId(),
                    date = Dates.startOfDay(System.currentTimeMillis()),
                    note = "Lunch"
                )
            )
        }
    }

    private fun seedIncome(cents: Long) {
        runBlocking {
            val salary = app().repository.getCategories()
                .first { it.name == "Salary" }
            app().repository.addTransaction(
                TransactionDraft(
                    amountCents = cents,
                    type = TransactionType.INCOME,
                    categoryId = salary.id,
                    date = Dates.startOfDay(System.currentTimeMillis())
                )
            )
        }
    }

    private fun foodCategoryId(): Long = runBlocking {
        app().repository.getCategories().first { it.name == "Food & Dining" }.id
    }
}
