package com.personalexpenses.tracker.ui.reports

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.R as MaterialR
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.databinding.FragmentReportsBinding
import com.personalexpenses.tracker.databinding.ItemCategoryBreakdownBinding
import com.personalexpenses.tracker.ui.common.CategoryBadge
import com.personalexpenses.tracker.ui.common.app
import com.personalexpenses.tracker.ui.common.appPrefs
import com.personalexpenses.tracker.ui.common.color
import com.personalexpenses.tracker.ui.common.repository
import com.personalexpenses.tracker.ui.common.themeColor
import com.personalexpenses.tracker.util.CategorySlice
import com.personalexpenses.tracker.util.Dates
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Monthly and yearly reports: totals, savings rate, spending trend chart and
 * a category breakdown for expenses or income.
 */
class ReportsFragment : Fragment(R.layout.fragment_reports) {

    private var _binding: FragmentReportsBinding? = null
    private val binding: FragmentReportsBinding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels {
        ReportsViewModel.factory(app().repository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentReportsBinding.bind(view)

        binding.togglePeriod.check(R.id.btn_period_month)
        binding.togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setPeriod(
                if (checkedId == R.id.btn_period_year) ReportPeriod.YEAR else ReportPeriod.MONTH
            )
        }
        binding.toggleBreakdown.check(R.id.btn_breakdown_expense)
        binding.toggleBreakdown.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setBreakdownType(
                if (checkedId == R.id.btn_breakdown_income) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
            )
        }
        binding.btnPrevPeriod.setOnClickListener { viewModel.previousPeriod() }
        binding.btnNextPeriod.setOnClickListener { viewModel.nextPeriod() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ReportsUiState) {
        val prefs = appPrefs()
        val isMonth = state.period == ReportPeriod.MONTH
        binding.tvPeriod.text = if (isMonth) {
            Dates.monthLabel(state.anchorMonth)
        } else {
            Dates.yearOf(state.anchorMonth).toString()
        }

        binding.cardSummary.isVisible = state.hasData
        binding.cardChart.isVisible = state.hasData
        binding.cardBreakdown.isVisible = state.hasData
        binding.emptyState.isVisible = !state.hasData

        binding.tvSummaryIncome.text = prefs.formatted(state.summary.incomeCents)
        binding.tvSummaryExpense.text = prefs.formatted(state.summary.expenseCents)
        binding.tvSummaryNet.text = prefs.formatted(state.summary.netCents)
        binding.tvSummaryNet.setTextColor(
            when {
                state.summary.netCents < 0L -> requireContext().color(R.color.expense_red)
                state.summary.netCents > 0L -> requireContext().color(R.color.income_green)
                else -> binding.tvSummaryNet.themeColor(
                    MaterialR.attr.colorOnSurface,
                    requireContext().color(R.color.brand_primary)
                )
            }
        )
        binding.tvSavingsRate.text = state.savingsRatePercent?.let { "$it%" }
            ?: getString(R.string.report_not_available)
        binding.tvAvgDaily.text = prefs.formatted(state.averageDailyExpenseCents)
        binding.tvTransactionCount.text = resources.getQuantityString(
            R.plurals.report_transactions_count,
            state.transactionCount,
            state.transactionCount
        )

        binding.chartTrend.submitData(state.trend)
        binding.chartTrend.contentDescription = buildString {
            append(getString(R.string.cd_trend_chart))
            append(". ")
            append(getString(R.string.report_expense))
            append(": ")
            append(prefs.formatted(state.summary.expenseCents))
        }

        binding.tvBreakdownTitle.setText(
            if (state.breakdownType == TransactionType.INCOME) {
                R.string.report_breakdown_income
            } else {
                R.string.report_breakdown_expenses
            }
        )

        val breakdownTotal = if (state.breakdownType == TransactionType.INCOME) {
            state.summary.incomeCents
        } else {
            state.summary.expenseCents
        }
        renderBreakdown(state.slices, breakdownTotal)
    }

    private fun renderBreakdown(slices: List<CategorySlice>, totalCents: Long) {
        val prefs = appPrefs()
        binding.breakdownContainer.removeAllViews()
        binding.tvBreakdownEmpty.isVisible = slices.isEmpty()
        slices.forEach { slice ->
            val item = ItemCategoryBreakdownBinding.inflate(
                layoutInflater,
                binding.breakdownContainer,
                false
            )
            CategoryBadge.apply(item.tvCategoryIcon, slice.iconKey, slice.colorHex)
            item.tvCategoryName.text = slice.name
            item.tvAmount.text = prefs.formatted(slice.totalCents)
            val percent = slice.sharePercentOf(totalCents)
            item.progressShare.progress = percent.coerceIn(0, 100)
            item.progressShare.setIndicatorColor(CategoryBadge.parseColor(slice.colorHex))
            item.tvShare.text = getString(R.string.report_of_total, percent)
            item.root.contentDescription = buildString {
                append(slice.name)
                append(", ").append(prefs.formatted(slice.totalCents))
                append(", ").append(getString(R.string.report_of_total, percent))
            }
            binding.breakdownContainer.addView(item.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.breakdownContainer.removeAllViews()
        _binding = null
    }
}
