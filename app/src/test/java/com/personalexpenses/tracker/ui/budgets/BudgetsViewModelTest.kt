package com.personalexpenses.tracker.ui.budgets

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.entity.BudgetEntity
import com.personalexpenses.tracker.data.entity.TransactionEntity
import com.personalexpenses.tracker.data.model.BudgetProgress
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.testing.FakeExpenseDataSource
import com.personalexpenses.tracker.testing.MainDispatcherRule
import com.personalexpenses.tracker.testing.dateAt
import com.personalexpenses.tracker.testing.dateTimeAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = dateTimeAt(2026, 11, 15, 10)

    private fun repository(): FakeExpenseDataSource {
        val repository = FakeExpenseDataSource()
        repository.setBudgets(
            listOf(
                BudgetEntity(id = 1, yearMonth = 202611, categoryId = 0, amountCents = 100_000),
                BudgetEntity(id = 2, yearMonth = 202611, categoryId = 1, amountCents = 20_000)
            )
        )
        repository.setTransactions(
            listOf(
                TransactionEntity(
                    id = 1,
                    amountCents = 5_000,
                    type = TransactionType.EXPENSE,
                    categoryId = 1,
                    date = dateAt(2026, 11, 2)
                ),
                TransactionEntity(
                    id = 2,
                    amountCents = 3_000,
                    type = TransactionType.EXPENSE,
                    categoryId = 2,
                    date = dateAt(2026, 11, 3)
                ),
                TransactionEntity(
                    id = 3,
                    amountCents = 1_000,
                    type = TransactionType.INCOME,
                    categoryId = 12,
                    date = dateAt(2026, 11, 4)
                )
            )
        )
        return repository
    }

    @Test
    fun `combines budgets with actual spending`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val viewModel = BudgetsViewModel(repository, now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.monthKey).isEqualTo(202611)
        assertThat(state.totalExpenseCents).isEqualTo(8_000L)
        assertThat(state.overall?.budgetCents).isEqualTo(100_000L)
        assertThat(state.overall?.spentCents).isEqualTo(8_000L)
        assertThat(state.overall?.isOverBudget).isFalse()
        assertThat(state.categoryBudgets).hasSize(1)
        assertThat(state.categoryBudgets.first().categoryName).isEqualTo("Food & Dining")
        assertThat(state.categoryBudgets.first().spentCents).isEqualTo(5_000L)
        assertThat(state.unbudgetedSpendCents).isEqualTo(3_000L)
        assertThat(state.availableCategories.map { it.id }).doesNotContain(1L)
        job.cancel()
    }

    @Test
    fun `setting and removing budgets updates state`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val viewModel = BudgetsViewModel(repository, now = { now })
        val events = mutableListOf<BudgetsEvent>()
        val stateJob = launch { viewModel.state.collect {} }
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.setBudget(2L, 25_000L)
        advanceUntilIdle()
        assertThat(repository.budgets().any { it.categoryId == 2L }).isTrue()
        assertThat(events).contains(BudgetsEvent.Saved)
        assertThat(viewModel.state.value.categoryBudgets.map { it.categoryId })
            .containsExactly(1L, 2L)

        viewModel.removeBudget(BudgetProgress.OVERALL_CATEGORY_ID)
        advanceUntilIdle()
        assertThat(repository.budgets().none { it.categoryId == 0L }).isTrue()
        assertThat(events).contains(BudgetsEvent.Removed)
        assertThat(viewModel.state.value.overall).isNull()

        stateJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun `ignores non positive budget values`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val viewModel = BudgetsViewModel(repository, now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.setBudget(2L, 0L)
        advanceUntilIdle()
        assertThat(repository.budgets().any { it.categoryId == 2L && it.amountCents == 0L })
            .isFalse()
        job.cancel()
    }

    @Test
    fun `month navigation recalculates for the new month`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = repository()
            val viewModel = BudgetsViewModel(repository, now = { now })
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            viewModel.nextMonth()
            advanceUntilIdle()
            assertThat(viewModel.state.value.monthKey).isEqualTo(202612)
            assertThat(viewModel.state.value.totalExpenseCents).isEqualTo(0L)
            assertThat(viewModel.state.value.overall).isNull()
            job.cancel()
        }
}
