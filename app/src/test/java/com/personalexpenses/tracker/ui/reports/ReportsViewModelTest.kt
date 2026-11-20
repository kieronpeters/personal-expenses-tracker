package com.personalexpenses.tracker.ui.reports

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.entity.TransactionEntity
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
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = dateTimeAt(2026, 11, 15, 10)

    private fun repository(): FakeExpenseDataSource {
        val repository = FakeExpenseDataSource()
        repository.setTransactions(
            listOf(
                TransactionEntity(
                    id = 1,
                    amountCents = 1_000,
                    type = TransactionType.EXPENSE,
                    categoryId = 1,
                    date = dateAt(2026, 11, 2)
                ),
                TransactionEntity(
                    id = 2,
                    amountCents = 2_000,
                    type = TransactionType.EXPENSE,
                    categoryId = 1,
                    date = dateAt(2026, 11, 3)
                ),
                TransactionEntity(
                    id = 3,
                    amountCents = 5_000,
                    type = TransactionType.INCOME,
                    categoryId = 12,
                    date = dateAt(2026, 11, 1)
                ),
                TransactionEntity(
                    id = 4,
                    amountCents = 500,
                    type = TransactionType.EXPENSE,
                    categoryId = 1,
                    date = dateAt(2026, 10, 5)
                )
            )
        )
        return repository
    }

    @Test
    fun `monthly report aggregates the selected month`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ReportsViewModel(repository(), now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.period).isEqualTo(ReportPeriod.MONTH)
        assertThat(state.anchorMonth).isEqualTo(202611)
        assertThat(state.summary.expenseCents).isEqualTo(3_000L)
        assertThat(state.summary.incomeCents).isEqualTo(5_000L)
        assertThat(state.summary.netCents).isEqualTo(2_000L)
        assertThat(state.savingsRatePercent).isEqualTo(40)
        assertThat(state.slices).hasSize(1)
        assertThat(state.slices.first().name).isEqualTo("Food & Dining")
        assertThat(state.slices.first().totalCents).isEqualTo(3_000L)
        assertThat(state.trend).hasSize(30)
        assertThat(state.averageDailyExpenseCents).isEqualTo(100L)
        assertThat(state.transactionCount).isEqualTo(3)
        assertThat(state.biggestExpense?.id).isEqualTo(2L)
        assertThat(state.hasData).isTrue()
        job.cancel()
    }

    @Test
    fun `yearly report aggregates the whole year`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ReportsViewModel(repository(), now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.setPeriod(ReportPeriod.YEAR)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.period).isEqualTo(ReportPeriod.YEAR)
        assertThat(state.summary.expenseCents).isEqualTo(3_500L)
        assertThat(state.summary.incomeCents).isEqualTo(5_000L)
        assertThat(state.trend).hasSize(12)
        assertThat(state.averageDailyExpenseCents).isEqualTo(3_500L / 365L)
        job.cancel()
    }

    @Test
    fun `breakdown can show income or expenses`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ReportsViewModel(repository(), now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.setBreakdownType(TransactionType.INCOME)
        advanceUntilIdle()
        assertThat(viewModel.state.value.breakdownType).isEqualTo(TransactionType.INCOME)
        assertThat(viewModel.state.value.slices.map { it.name }).containsExactly("Salary")

        viewModel.setBreakdownType(TransactionType.EXPENSE)
        advanceUntilIdle()
        assertThat(viewModel.state.value.slices.map { it.name }).containsExactly("Food & Dining")
        job.cancel()
    }

    @Test
    fun `period navigation moves by month or year`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ReportsViewModel(repository(), now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.previousPeriod()
        advanceUntilIdle()
        assertThat(viewModel.state.value.anchorMonth).isEqualTo(202610)
        assertThat(viewModel.state.value.hasData).isTrue()

        viewModel.nextPeriod()
        viewModel.nextPeriod()
        advanceUntilIdle()
        assertThat(viewModel.state.value.anchorMonth).isEqualTo(202612)
        assertThat(viewModel.state.value.hasData).isFalse()

        viewModel.goToCurrentPeriod()
        advanceUntilIdle()
        assertThat(viewModel.state.value.anchorMonth).isEqualTo(202611)

        viewModel.setPeriod(ReportPeriod.YEAR)
        advanceUntilIdle()
        viewModel.nextPeriod()
        advanceUntilIdle()
        assertThat(viewModel.state.value.anchorMonth).isEqualTo(202711)
        job.cancel()
    }
}
