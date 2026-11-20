package com.personalexpenses.tracker.ui.transactions

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
import com.personalexpenses.tracker.util.Dates
import com.personalexpenses.tracker.util.ReportCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Item model of the transaction list: either a day heading or a transaction. */
sealed interface TransactionListItem {
    data class DateHeader(val label: String) : TransactionListItem

    data class Entry(val row: TransactionRow) : TransactionListItem
}

data class TransactionsUiState(
    val monthKey: Int,
    val typeFilter: TransactionType? = null,
    val query: String = "",
    val items: List<TransactionListItem> = emptyList(),
    val totals: PeriodTotals = PeriodTotals.ZERO,
    val monthHasData: Boolean = false,
    val isLoading: Boolean = true
) {
    val hasActiveFilter: Boolean
        get() = typeFilter != null || query.isNotBlank()
}

sealed interface TransactionsEvent {
    data class Deleted(val row: TransactionRow) : TransactionsEvent

    data object Restored : TransactionsEvent
}

/**
 * Drives the transactions screen: month navigation, type/search filters,
 * period totals and delete/undo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val repository: ExpenseDataSource,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val monthKey = MutableStateFlow(Dates.currentMonthKey(now()))
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val query = MutableStateFlow("")

    private val _events = MutableSharedFlow<TransactionsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TransactionsEvent> = _events.asSharedFlow()

    val state: StateFlow<TransactionsUiState> = combine(
        monthKey,
        typeFilter,
        query
    ) { month, type, text -> Triple(month, type, text) }
        .flatMapLatest { (month, type, text) ->
            val start = Dates.startOfMonth(month)
            val end = Dates.endOfMonthExclusive(month)
            val monthRows = repository.observeTransactions(TransactionFilter(start, end))
            val filteredRows = repository.observeTransactions(
                TransactionFilter(
                    startInclusive = start,
                    endExclusive = end,
                    type = type,
                    query = text.trim()
                )
            )
            combine(monthRows, filteredRows) { all, filtered ->
                buildState(month, type, text, all, filtered)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsUiState(
                monthKey = Dates.currentMonthKey(now()),
                isLoading = true
            )
        )

    private fun buildState(
        month: Int,
        type: TransactionType?,
        text: String,
        allRows: List<TransactionRow>,
        filteredRows: List<TransactionRow>
    ): TransactionsUiState {
        val items = buildList {
            var lastDay = Long.MIN_VALUE
            for (row in filteredRows) {
                if (row.date != lastDay) {
                    add(TransactionListItem.DateHeader(Dates.dayHeader(row.date, now())))
                    lastDay = row.date
                }
                add(TransactionListItem.Entry(row))
            }
        }
        return TransactionsUiState(
            monthKey = month,
            typeFilter = type,
            query = text,
            items = items,
            totals = ReportCalculator.summarize(allRows),
            monthHasData = allRows.isNotEmpty(),
            isLoading = false
        )
    }

    fun previousMonth() {
        monthKey.update { Dates.shiftMonth(it, -1) }
    }

    fun nextMonth() {
        monthKey.update { Dates.shiftMonth(it, 1) }
    }

    fun goToCurrentMonth() {
        monthKey.value = Dates.currentMonthKey(now())
    }

    fun setTypeFilter(type: TransactionType?) {
        typeFilter.value = type
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun delete(row: TransactionRow) {
        viewModelScope.launch {
            repository.deleteTransaction(row.id)
            _events.emit(TransactionsEvent.Deleted(row))
        }
    }

    fun restore(row: TransactionRow) {
        viewModelScope.launch {
            repository.restoreTransaction(row)
            _events.emit(TransactionsEvent.Restored)
        }
    }

    companion object {
        fun factory(repository: ExpenseDataSource): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransactionsViewModel(repository) }
        }
    }
}
