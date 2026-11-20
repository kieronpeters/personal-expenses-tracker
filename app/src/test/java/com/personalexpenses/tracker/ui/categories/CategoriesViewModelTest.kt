package com.personalexpenses.tracker.ui.categories

import com.google.common.truth.Truth.assertThat
import com.personalexpenses.tracker.data.entity.TransactionEntity
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.testing.FakeExpenseDataSource
import com.personalexpenses.tracker.testing.MainDispatcherRule
import com.personalexpenses.tracker.testing.dateAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `lists categories grouped by type`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = CategoriesViewModel(repository)
        val job = launch { viewModel.items.collect {} }
        advanceUntilIdle()

        val items = viewModel.items.value
        assertThat(items.first()).isInstanceOf(CategoryListItem.Header::class.java)
        val entries = items.filterIsInstance<CategoryListItem.Entry>()
        assertThat(entries).hasSize(16)
        assertThat(entries.count { it.category.type == TransactionType.EXPENSE }).isEqualTo(11)
        assertThat(entries.count { it.category.type == TransactionType.INCOME }).isEqualTo(5)
        job.cancel()
    }

    @Test
    fun `creates and updates categories`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = CategoriesViewModel(repository)
        val events = mutableListOf<CategoriesEvent>()
        val itemsJob = launch { viewModel.items.collect {} }
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.create("Coffee", TransactionType.EXPENSE, "#EF4444", "☕")
        advanceUntilIdle()
        assertThat(events).contains(CategoriesEvent.Created)
        val coffee = repository.categories().first { it.name == "Coffee" }

        viewModel.update(coffee, "Coffee & Tea", "#3B82F6", "☕")
        advanceUntilIdle()
        assertThat(events).contains(CategoriesEvent.Updated)
        assertThat(repository.categories().first { it.id == coffee.id }.name)
            .isEqualTo("Coffee & Tea")

        itemsJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun `duplicate names are rejected per type`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = CategoriesViewModel(repository)
        val events = mutableListOf<CategoriesEvent>()
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.create("Food & Dining", TransactionType.EXPENSE, "#EF4444", "🍔")
        advanceUntilIdle()
        assertThat(events).contains(CategoriesEvent.Duplicate)

        // The same name is allowed for a different type.
        viewModel.create("Food & Dining", TransactionType.INCOME, "#EF4444", "🍔")
        advanceUntilIdle()
        assertThat(events).contains(CategoriesEvent.Created)
        eventJob.cancel()
    }

    @Test
    fun `cannot delete a category that is in use`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        repository.setTransactions(
            listOf(
                TransactionEntity(
                    id = 1,
                    amountCents = 100,
                    type = TransactionType.EXPENSE,
                    categoryId = 1,
                    date = dateAt(2026, 11, 2)
                )
            )
        )
        val viewModel = CategoriesViewModel(repository)
        val events = mutableListOf<CategoriesEvent>()
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        val food = repository.categories().first { it.id == 1L }
        viewModel.delete(food)
        advanceUntilIdle()

        assertThat(events).contains(CategoriesEvent.InUse)
        assertThat(repository.categories().any { it.id == 1L }).isTrue()
        eventJob.cancel()
    }

    @Test
    fun `deletes an unused category`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenseDataSource()
        val viewModel = CategoriesViewModel(repository)
        val events = mutableListOf<CategoriesEvent>()
        val eventJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        val food = repository.categories().first { it.id == 1L }
        viewModel.delete(food)
        advanceUntilIdle()

        assertThat(events).contains(CategoriesEvent.Deleted)
        assertThat(repository.categories().any { it.id == food.id }).isFalse()
        eventJob.cancel()
    }
}
