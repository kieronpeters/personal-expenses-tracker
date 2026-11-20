package com.personalexpenses.tracker.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
 * End-to-end tests for budgeting and reporting: creating budgets through the
 * dialogs, verifying persisted values, and checking report totals, breakdown
 * and period switching.
 */
@RunWith(AndroidJUnit4::class)
class BudgetAndReportFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        resetUserData()
    }

    @Test
    fun settingOverallBudgetShowsItAndPersists() {
        openBudgets()
        onView(withId(R.id.btn_edit_overall)).perform(click())
        onView(withId(R.id.et_amount)).perform(replaceText("1000"))
        onView(withText(R.string.save)).perform(click())

        UiWait.until {
            onView(withId(R.id.tv_overall_status))
                .check(matches(withText(containsString("1,000.00"))))
        }

        val budgets = runBlocking { app().repository.getBudgets(Dates.currentMonthKey()) }
        assertThat(budgets).hasSize(1)
        assertThat(budgets.single().categoryId).isEqualTo(0L)
        assertThat(budgets.single().amountCents).isEqualTo(100_000L)
    }

    @Test
    fun addingCategoryBudgetShowsRowAndPersists() {
        openBudgets()
        onView(withId(R.id.btn_add_category_budget)).perform(click())
        onView(withId(R.id.et_amount)).perform(replaceText("250"))
        onView(withText(R.string.save)).perform(click())

        UiWait.until {
            onView(withId(R.id.tv_amounts))
                .check(matches(withText(containsString("250.00"))))
        }

        val budgets = runBlocking { app().repository.getBudgets(Dates.currentMonthKey()) }
        assertThat(budgets).hasSize(1)
        assertThat(budgets.single().categoryId).isNotEqualTo(0L)
        assertThat(budgets.single().amountCents).isEqualTo(25_000L)
    }

    @Test
    fun budgetProgressReflectsActualSpending() {
        seedExpense(5_000L)
        openBudgets()
        onView(withId(R.id.btn_edit_overall)).perform(click())
        onView(withId(R.id.et_amount)).perform(replaceText("100"))
        onView(withText(R.string.save)).perform(click())

        UiWait.until {
            onView(withId(R.id.tv_overall_detail))
                .check(matches(withText(containsString("50.00"))))
        }
        onView(withId(R.id.tv_overall_amount))
            .check(matches(withText(containsString("50.00"))))
    }

    @Test
    fun overBudgetSpendingReportsAnOverBudgetState() {
        seedExpense(5_000L)
        openBudgets()
        onView(withId(R.id.btn_edit_overall)).perform(click())
        onView(withId(R.id.et_amount)).perform(replaceText("10"))
        onView(withText(R.string.save)).perform(click())

        UiWait.until {
            onView(withId(R.id.tv_overall_detail))
                .check(matches(withText(containsString("over budget"))))
        }
    }

    @Test
    fun monthlyReportShowsTotalsAndCategoryBreakdown() {
        seedExpense(3_000L)
        seedIncome(5_000L)
        openReports()

        UiWait.until {
            onView(withId(R.id.tv_summary_expense))
                .check(matches(withText(containsString("30.00"))))
        }
        onView(withId(R.id.tv_summary_income))
            .check(matches(withText(containsString("50.00"))))
        onView(withId(R.id.tv_summary_net))
            .check(matches(withText(containsString("20.00"))))
        onView(
            allOf(withId(R.id.tv_category_name), withText("Food & Dining"))
        ).check(matches(isDisplayed()))

        onView(withId(R.id.btn_breakdown_income)).perform(click())
        UiWait.until {
            onView(allOf(withId(R.id.tv_category_name), withText("Salary")))
                .check(matches(isDisplayed()))
        }

        onView(withId(R.id.btn_period_year)).perform(click())
        val year = Dates.yearOf(Dates.currentMonthKey()).toString()
        UiWait.until {
            onView(withId(R.id.tv_period)).check(matches(withText(year)))
        }
        onView(withId(R.id.tv_summary_expense))
            .check(matches(withText(containsString("30.00"))))
    }

    @Test
    fun reportWithoutDataShowsEmptyState() {
        openReports()
        onView(withId(R.id.empty_state)).check(matches(isDisplayed()))
    }

    private fun openBudgets() {
        onView(withId(R.id.budgetsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.btn_add_category_budget)).check(matches(isDisplayed()))
        }
    }

    private fun openReports() {
        onView(withId(R.id.reportsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.toggle_period)).check(matches(isDisplayed()))
        }
    }

    private fun seedExpense(cents: Long) {
        runBlocking {
            val category = app().repository.getCategories()
                .first { it.name == "Food & Dining" }
            app().repository.addTransaction(
                TransactionDraft(
                    amountCents = cents,
                    type = TransactionType.EXPENSE,
                    categoryId = category.id,
                    date = Dates.startOfDay(System.currentTimeMillis())
                )
            )
        }
    }

    private fun seedIncome(cents: Long) {
        runBlocking {
            val category = app().repository.getCategories()
                .first { it.name == "Salary" }
            app().repository.addTransaction(
                TransactionDraft(
                    amountCents = cents,
                    type = TransactionType.INCOME,
                    categoryId = category.id,
                    date = Dates.startOfDay(System.currentTimeMillis())
                )
            )
        }
    }
}
