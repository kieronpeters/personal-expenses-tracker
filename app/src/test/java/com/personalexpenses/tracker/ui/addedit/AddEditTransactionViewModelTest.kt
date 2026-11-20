package com.personalexpenses.tracker.ui.addedit

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.R
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
class AddEditTransactionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = dateTimeAt(2026, 11, 15, 10)

    @Test
    fun `new transaction defaults to today and first category`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeExpenseDataSource()
            val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.isEdit).isFalse()
            assertThat(state.type).isEqualTo(TransactionType.EXPENSE)
            assertThat(state.date).isEqualTo(DatesStartOfToday())
            assertThat(state.categoryId).isEqualTo(1L)
            assertThat(state.categories).isNotEmpty()
        }

    @Test
    fun `saves a valid expense`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
        advanceUntilIdle()

        viewModel.onAmountChanged("12.34")
        viewModel.onNoteChanged("  Coffee with team  ")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.saved).isTrue()
        assertThat(viewModel.state.value.saving).isFalse()
        assertThat(repository.addTransactionCalls).isEqualTo(1)
        val saved = repository.transactions().single()
        assertThat(saved.amountCents).isEqualTo(1_234L)
        assertThat(saved.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(saved.categoryId).isEqualTo(1L)
        assertThat(saved.note).isEqualTo("Coffee with team")
    }

    @Test
    fun `rejects an invalid amount`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
        advanceUntilIdle()

        viewModel.onAmountChanged("0")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.amountError).isEqualTo(R.string.error_invalid_amount)
        assertThat(viewModel.state.value.saved).isFalse()
        assertThat(repository.addTransactionCalls).isEqualTo(0)

        viewModel.onAmountChanged("abc")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.state.value.amountError).isEqualTo(R.string.error_invalid_amount)
    }

    @Test
    fun `requires a category`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        repository.setCategories(emptyList())
        val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
        advanceUntilIdle()

        viewModel.onAmountChanged("10.00")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.categoryError).isEqualTo(R.string.error_category_required)
        assertThat(repository.addTransactionCalls).isEqualTo(0)
    }

    @Test
    fun `switching type selects a category of that type`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeExpenseDataSource()
            val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
            advanceUntilIdle()

            viewModel.onTypeChanged(TransactionType.INCOME)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.type).isEqualTo(TransactionType.INCOME)
            assertThat(state.selectedCategory?.type).isEqualTo(TransactionType.INCOME)
            assertThat(state.categories.all { it.type == TransactionType.INCOME }).isTrue()
        }

    @Test
    fun `loads and updates an existing transaction`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        repository.setTransactions(
            listOf(
                TransactionEntity(
                    id = 500L,
                    amountCents = 1_000L,
                    type = TransactionType.EXPENSE,
                    categoryId = 1L,
                    date = dateAt(2026, 11, 3),
                    note = "Lunch"
                )
            )
        )
        val viewModel = AddEditTransactionViewModel(repository, 500L, 2, now = { now })
        advanceUntilIdle()

        assertThat(viewModel.state.value.isEdit).isTrue()
        assertThat(viewModel.state.value.amountText).isEqualTo("10.00")
        assertThat(viewModel.state.value.note).isEqualTo("Lunch")
        assertThat(viewModel.state.value.categoryId).isEqualTo(1L)

        viewModel.onAmountChanged("20.00")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.saved).isTrue()
        assertThat(repository.updateTransactionCalls).isEqualTo(1)
        assertThat(repository.transactions().single().amountCents).isEqualTo(2_000L)
    }

    @Test
    fun `missing transaction is reported`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = AddEditTransactionViewModel(repository, 999L, 2, now = { now })
        advanceUntilIdle()

        assertThat(viewModel.state.value.notFound).isTrue()
    }

    @Test
    fun `creates a category and selects it`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
        val events = mutableListOf<AddEditEvent>()
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.createCategory("Coffee", TransactionType.EXPENSE, "#EF4444", "☕")
        advanceUntilIdle()

        val created = events.filterIsInstance<AddEditEvent.CategoryCreated>().single()
        assertThat(viewModel.state.value.categoryId).isEqualTo(created.categoryId)
        assertThat(repository.categories().any { it.name == "Coffee" }).isTrue()
        eventJob.cancel()
    }

    @Test
    fun `duplicate category name is reported`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = AddEditTransactionViewModel(repository, 0L, 2, now = { now })
        val events = mutableListOf<AddEditEvent>()
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.createCategory("Food & Dining", TransactionType.EXPENSE, "#EF4444", "🍔")
        advanceUntilIdle()

        assertThat(events).containsExactly(AddEditEvent.CategoryDuplicate)
        eventJob.cancel()
    }

    private fun DatesStartOfToday(): Long =
        com.personalexpenses.tracker.util.Dates.startOfDay(now)
}
