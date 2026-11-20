package com.personalexpenses.tracker.ui.transactions

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
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = dateTimeAt(2026, 11, 15, 10)

    private fun transaction(
        id: Long,
        cents: Long,
        type: TransactionType,
        categoryId: Long,
        date: Long,
        note: String = ""
    ) = TransactionEntity(
        id = id,
        amountCents = cents,
        type = type,
        categoryId = categoryId,
        date = date,
        note = note
    )

    private fun repositoryWithData(): FakeExpenseDataSource {
        val repository = FakeExpenseDataSource()
        repository.setTransactions(
            listOf(
                transaction(1, 1_000, TransactionType.EXPENSE, 1, dateAt(2026, 11, 2), "Lunch"),
                transaction(2, 5_000, TransactionType.INCOME, 12, dateAt(2026, 11, 1)),
                transaction(3, 999, TransactionType.EXPENSE, 1, dateAt(2026, 10, 31), "Old")
            )
        )
        return repository
    }

    @Test
    fun `computes month totals and groups items by day`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repositoryWithData()
        val viewModel = TransactionsViewModel(repository, now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.monthKey).isEqualTo(202611)
        assertThat(state.monthHasData).isTrue()
        assertThat(state.totals.expenseCents).isEqualTo(1_000L)
        assertThat(state.totals.incomeCents).isEqualTo(5_000L)

        val entries = state.items.filterIsInstance<TransactionListItem.Entry>()
        val headers = state.items.filterIsInstance<TransactionListItem.DateHeader>()
        assertThat(entries).hasSize(2)
        assertThat(headers).hasSize(2)
        assertThat(headers.first().label).isEqualTo("Nov 2")
        job.cancel()
    }

    @Test
    fun `month navigation moves to adjacent months`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repositoryWithData()
        val viewModel = TransactionsViewModel(repository, now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.nextMonth()
        advanceUntilIdle()
        assertThat(viewModel.state.value.monthKey).isEqualTo(202612)
        assertThat(viewModel.state.value.items).isEmpty()

        viewModel.previousMonth()
        viewModel.previousMonth()
        advanceUntilIdle()
        assertThat(viewModel.state.value.monthKey).isEqualTo(202610)
        assertThat(
            viewModel.state.value.items.filterIsInstance<TransactionListItem.Entry>()
        ).hasSize(1)
        job.cancel()
    }

    @Test
    fun `type filter narrows the list but not the month totals`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = repositoryWithData()
            val viewModel = TransactionsViewModel(repository, now = { now })
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            viewModel.setTypeFilter(TransactionType.EXPENSE)
            advanceUntilIdle()
            val entries = viewModel.state.value.items
                .filterIsInstance<TransactionListItem.Entry>()
            assertThat(entries).hasSize(1)
            assertThat(entries.first().row.type).isEqualTo(TransactionType.EXPENSE)
            assertThat(viewModel.state.value.totals.incomeCents).isEqualTo(5_000L)
            assertThat(viewModel.state.value.hasActiveFilter).isTrue()
            job.cancel()
        }

    @Test
    fun `search matches notes and category names`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repositoryWithData()
        val viewModel = TransactionsViewModel(repository, now = { now })
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.setQuery("lunch")
        advanceUntilIdle()
        assertThat(viewModel.state.value.items.filterIsInstance<TransactionListItem.Entry>())
            .hasSize(1)

        viewModel.setQuery("Salary")
        advanceUntilIdle()
        val entries = viewModel.state.value.items
            .filterIsInstance<TransactionListItem.Entry>()
        assertThat(entries).hasSize(1)
        assertThat(entries.first().row.categoryName).isEqualTo("Salary")
        job.cancel()
    }

    @Test
    fun `delete removes the row and undo restores it`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repositoryWithData()
        val viewModel = TransactionsViewModel(repository, now = { now })
        val events = mutableListOf<TransactionsEvent>()
        val stateJob = launch { viewModel.state.collect {} }
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        val row = viewModel.state.value.items
            .filterIsInstance<TransactionListItem.Entry>()
            .first().row
        viewModel.delete(row)
        advanceUntilIdle()

        assertThat(repository.deleteTransactionCalls).isEqualTo(1)
        assertThat(repository.transactions()).hasSize(2)
        assertThat(viewModel.state.value.items
            .filterIsInstance<TransactionListItem.Entry>()).hasSize(1)
        assertThat(events.filterIsInstance<TransactionsEvent.Deleted>()).hasSize(1)

        viewModel.restore(row)
        advanceUntilIdle()
        assertThat(repository.transactions()).hasSize(3)
        assertThat(events.filterIsInstance<TransactionsEvent.Restored>()).hasSize(1)
        stateJob.cancel()
        eventJob.cancel()
    }
}
