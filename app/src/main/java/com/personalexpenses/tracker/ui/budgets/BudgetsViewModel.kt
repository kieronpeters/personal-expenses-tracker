package com.personalexpenses.tracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.BudgetProgress
import com.personalexpenses.tracker.data.model.TransactionFilter
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.util.Dates
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

data class BudgetsUiState(
    val monthKey: Int,
    val overall: BudgetProgress? = null,
    val categoryBudgets: List<BudgetProgress> = emptyList(),
    val allExpenseCategories: List<CategoryEntity> = emptyList(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val totalExpenseCents: Long = 0L,
    val unbudgetedSpendCents: Long = 0L
) {
    val hasAnyBudget: Boolean get() = overall != null || categoryBudgets.isNotEmpty()
}

sealed interface BudgetsEvent {
    data object Saved : BudgetsEvent

    data object Removed : BudgetsEvent
}

/**
 * Combines the budgets of the selected month with that month's actual
 * spending, producing one progress row per budget plus the overall budget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    private val repository: ExpenseDataSource,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val monthKey = MutableStateFlow(Dates.currentMonthKey(now()))

    private val _events = MutableSharedFlow<BudgetsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BudgetsEvent> = _events.asSharedFlow()

    val state: StateFlow<BudgetsUiState> = monthKey.flatMapLatest { month ->
        val start = Dates.startOfMonth(month)
        val end = Dates.endOfMonthExclusive(month)
        combine(
            repository.observeBudgets(month),
            repository.observeTransactions(
                TransactionFilter(start, end, type = TransactionType.EXPENSE)
            ),
            repository.observeCategories(TransactionType.EXPENSE)
        ) { budgets, expenseRows, categories ->
            buildState(month, budgets, expenseRows, categories)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsUiState(
            monthKey = Dates.currentMonthKey(now()),
            allExpenseCategories = emptyList()
        )
    )

    private fun buildState(
        month: Int,
        budgets: List<com.personalexpenses.tracker.data.entity.BudgetEntity>,
        expenseRows: List<com.personalexpenses.tracker.data.model.TransactionRow>,
        categories: List<CategoryEntity>
    ): BudgetsUiState {
        val spentByCategory = expenseRows.groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
        val totalExpense = expenseRows.sumOf { it.amountCents }

        val overallBudget = budgets.firstOrNull {
            it.categoryId == BudgetProgress.OVERALL_CATEGORY_ID
        }
        val overall = overallBudget?.let {
            BudgetProgress(
                categoryId = BudgetProgress.OVERALL_CATEGORY_ID,
                categoryName = "Overall",
                categoryColor = "#0F766E",
                categoryIcon = "\uD83D\uDCB0",
                budgetCents = it.amountCents,
                spentCents = totalExpense
            )
        }

        val categoryBudgets = budgets
            .filter { it.categoryId != BudgetProgress.OVERALL_CATEGORY_ID }
            .mapNotNull { budget ->
                val category = categories.firstOrNull { it.id == budget.categoryId }
                    ?: return@mapNotNull null
                BudgetProgress(
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryColor = category.colorHex,
                    categoryIcon = category.iconKey,
                    budgetCents = budget.amountCents,
                    spentCents = spentByCategory[category.id] ?: 0L
                )
            }
            .sortedWith(compareByDescending<BudgetProgress> { it.usedFraction }.thenBy { it.categoryName })

        val budgetedIds = budgets.map { it.categoryId }.toSet()
        val available = categories.filter { it.id !in budgetedIds }
        val unbudgetedSpend = expenseRows
            .filter { it.categoryId !in budgetedIds }
            .sumOf { it.amountCents }

        return BudgetsUiState(
            monthKey = month,
            overall = overall,
            categoryBudgets = categoryBudgets,
            allExpenseCategories = categories,
            availableCategories = available,
            totalExpenseCents = totalExpense,
            unbudgetedSpendCents = unbudgetedSpend
        )
    }

    fun previousMonth() {
        monthKey.update { Dates.shiftMonth(it, -1) }
    }

    fun nextMonth() {
        monthKey.update { Dates.shiftMonth(it, 1) }
    }

    fun setBudget(categoryId: Long, amountCents: Long) {
        if (amountCents <= 0L) return
        viewModelScope.launch {
            repository.setBudget(monthKey.value, categoryId, amountCents)
            _events.emit(BudgetsEvent.Saved)
        }
    }

    fun removeBudget(categoryId: Long) {
        viewModelScope.launch {
            repository.removeBudget(monthKey.value, categoryId)
            _events.emit(BudgetsEvent.Removed)
        }
    }

    companion object {
        fun factory(repository: ExpenseDataSource): ViewModelProvider.Factory = viewModelFactory {
            initializer { BudgetsViewModel(repository) }
        }
    }
}
