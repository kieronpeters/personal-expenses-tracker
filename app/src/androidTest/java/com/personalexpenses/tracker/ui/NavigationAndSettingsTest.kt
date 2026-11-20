package com.personalexpenses.tracker.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
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
import com.personalexpenses.tracker.resetUserData
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation and settings flows: bottom navigation, the category manager and
 * creating a custom category through the real dialogs.
 */
@RunWith(AndroidJUnit4::class)
class NavigationAndSettingsTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        resetUserData()
    }

    @Test
    fun bottomNavigationSwitchesBetweenTopLevelScreens() {
        onView(withId(R.id.budgetsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.btn_add_category_budget)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.reportsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.toggle_period)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.settingsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.row_currency)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.transactionsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.tv_month)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settingsShowsVersionCurrencyAndOpensCategories() {
        openSettings()

        onView(withId(R.id.tv_version)).check(matches(withText(containsString("Version"))))
        onView(withId(R.id.tv_currency_value)).check(matches(withText(containsString("USD"))))

        onView(withId(R.id.row_categories)).perform(click())
        UiWait.until {
            onView(withId(R.id.rv_categories)).check(matches(isDisplayed()))
        }
        onView(withText(R.string.category_new)).check(matches(isDisplayed()))
        onView(withText(R.string.categories_expenses_header))
            .check(matches(isDisplayed()))
    }

    @Test
    fun creatingACategoryAddsItToListAndDatabase() {
        openSettings()
        onView(withId(R.id.row_categories)).perform(click())
        UiWait.until {
            onView(withId(R.id.fab_add_category)).check(matches(isDisplayed()))
        }

        onView(withId(R.id.fab_add_category)).perform(click())
        onView(withId(R.id.et_name)).perform(replaceText("Coffee"))
        onView(withText(R.string.save)).perform(click())

        UiWait.until {
            onView(allOf(withId(R.id.tv_category_name), withText("Coffee")))
                .check(matches(isDisplayed()))
        }

        val categories = runBlocking { app().repository.getCategories() }
        assertThat(categories.any { it.name == "Coffee" }).isTrue()
    }

    @Test
    fun addTransactionHidesBottomNavigationAndBackReturns() {
        onView(withId(R.id.fab_add)).perform(click())
        UiWait.until {
            onView(withId(R.id.btn_save)).check(matches(isDisplayed()))
        }
        onView(withId(R.id.bottom_nav)).check(matches(not(isDisplayed())))

        pressBack()
        UiWait.until {
            onView(withId(R.id.tv_month)).check(matches(isDisplayed()))
        }
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
    }

    private fun openSettings() {
        onView(withId(R.id.settingsFragment)).perform(click())
        UiWait.until {
            onView(withId(R.id.row_currency)).check(matches(isDisplayed()))
        }
    }
}
