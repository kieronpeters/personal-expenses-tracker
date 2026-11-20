package com.personalexpenses.tracker.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.personalexpenses.tracker.ExpenseTrackerApp
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.SeedData
import com.personalexpenses.tracker.util.Prefs
import kotlin.math.roundToInt

/** Convenience accessors shared by all fragments. */
fun Fragment.app(): ExpenseTrackerApp =
    requireContext().applicationContext as ExpenseTrackerApp

fun Fragment.repository(): ExpenseDataSource = app().repository

fun Fragment.appPrefs(): Prefs = app().prefs

fun Fragment.showMessage(@StringRes messageRes: Int) {
    val view = view ?: return
    Snackbar.make(view, messageRes, Snackbar.LENGTH_LONG).show()
}

fun Fragment.showMessage(message: String) {
    val view = view ?: return
    Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
}

fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun Context.color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

/** Resolves a theme color attribute, falling back to a literal color. */
fun View.themeColor(attrRes: Int, fallback: Int): Int =
    MaterialColors.getColor(this, attrRes, fallback)

/**
 * Renders the round, tinted category badge that contains the category emoji.
 */
object CategoryBadge {

    private const val FALLBACK_COLOR = "#6B7280"
    private const val BADGE_ALPHA = 52

    fun parseColor(colorHex: String): Int = try {
        Color.parseColor(colorHex)
    } catch (e: IllegalArgumentException) {
        Color.parseColor(FALLBACK_COLOR)
    }

    fun apply(view: TextView, iconKey: String, colorHex: String) {
        view.text = iconKey.ifBlank { SeedData.DEFAULT_ICON }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(parseColor(colorHex), BADGE_ALPHA))
        }
    }
}

/** Safe access to the parent fragment manager for nested navigation. */
fun Fragment.navigationManager(): FragmentManager = parentFragmentManager
