package com.personalexpenses.tracker.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.model.PeriodTotals
import com.personalexpenses.tracker.data.model.TransactionFilter
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.util.CategorySlice
import com.personalexpenses.tracker.util.Dates
import com.personalexpenses.tracker.util.ReportCalculator
import com.personalexpenses.tracker.util.TrendPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ReportPeriod { MONTH, YEAR }

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.MONTH,
    val anchorMonth: Int = Dates.currentMonthKey(),
    val breakdownType: TransactionType = TransactionType.EXPENSE,
    val summary: PeriodTotals = PeriodTotals.ZERO,
    val slices: List<CategorySlice> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val averageDailyExpenseCents: Long = 0L,
    val transactionCount: Int = 0,
    val biggestExpense: TransactionRow? = null,
    val hasData: Boolean = false
) {
    val savingsRatePercent: Int? get() = summary.savingsRatePercent
}

/**
 * Aggregates the transactions of the selected month or year into the numbers
 * shown by the reports screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val repository: ExpenseDataSource,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.MONTH)
    private val anchorMonth = MutableStateFlow(Dates.currentMonthKey(now()))
    private val breakdownType = MutableStateFlow(TransactionType.EXPENSE)

    val state: StateFlow<ReportsUiState> = combine(
        period,
        anchorMonth,
        breakdownType
    ) { selectedPeriod, anchor, chosenType -> Triple(selectedPeriod, anchor, chosenType) }
        .flatMapLatest { (selectedPeriod, anchor, chosenType) ->
            val year = Dates.yearOf(anchor)
            val start = if (selectedPeriod == ReportPeriod.MONTH) {
                Dates.startOfMonth(anchor)
            } else {
                Dates.startOfYear(year)
            }
            val end = if (selectedPeriod == ReportPeriod.MONTH) {
                Dates.endOfMonthExclusive(anchor)
            } else {
                Dates.endOfYearExclusive(year)
            }
            val days = if (selectedPeriod == ReportPeriod.MONTH) {
                Dates.daysInMonth(anchor)
            } else {
                Dates.daysInYear(year)
            }
            repository.observeTransactions(TransactionFilter(start, end)).map { rows ->
                val summary = ReportCalculator.summarize(rows)
                ReportsUiState(
                    period = selectedPeriod,
                    anchorMonth = anchor,
                    breakdownType = chosenType,
                    summary = summary,
                    slices = ReportCalculator.categoryBreakdown(rows, chosenType),
                    trend = if (selectedPeriod == ReportPeriod.MONTH) {
                        ReportCalculator.dailyTrend(rows, anchor)
                    } else {
                        ReportCalculator.monthlyTrend(rows, year)
                    },
                    averageDailyExpenseCents = ReportCalculator.averageDailyExpense(
                        summary.expenseCents,
                        days
                    ),
                    transactionCount = rows.size,
                    biggestExpense = ReportCalculator.biggestExpense(rows),
                    hasData = rows.isNotEmpty()
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(anchorMonth = Dates.currentMonthKey(now()))
        )

    fun setPeriod(newPeriod: ReportPeriod) {
        period.value = newPeriod
    }

    fun previousPeriod() {
        anchorMonth.update { anchor ->
            Dates.shiftMonth(anchor, if (period.value == ReportPeriod.MONTH) -1 else -12)
        }
    }

    fun nextPeriod() {
        anchorMonth.update { anchor ->
            Dates.shiftMonth(anchor, if (period.value == ReportPeriod.MONTH) 1 else 12)
        }
    }

    fun goToCurrentPeriod() {
        anchorMonth.value = Dates.currentMonthKey(now())
    }

    fun setBreakdownType(type: TransactionType) {
        breakdownType.value = type
    }

    companion object {
        fun factory(repository: ExpenseDataSource): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportsViewModel(repository) }
        }
    }
}
