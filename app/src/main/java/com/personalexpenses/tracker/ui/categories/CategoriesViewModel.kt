package com.personalexpenses.tracker.ui.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.CategoryDeleteResult
import com.personalexpenses.tracker.data.model.CategorySaveResult
import com.personalexpenses.tracker.data.model.TransactionType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryListItem {
    data class Header(@StringRes val titleRes: Int) : CategoryListItem

    data class Entry(val category: CategoryEntity) : CategoryListItem
}

sealed interface CategoriesEvent {
    data object Created : CategoriesEvent

    data object Updated : CategoriesEvent

    data object Duplicate : CategoriesEvent

    data object Deleted : CategoriesEvent

    data object InUse : CategoriesEvent

    data object Failed : CategoriesEvent
}

/**
 * Backs the category manager. Categories are grouped by type so expense and
 * income categories stay visually separated.
 */
class CategoriesViewModel(
    private val repository: ExpenseDataSource
) : ViewModel() {

    val items: StateFlow<List<CategoryListItem>> = repository.observeCategories()
        .map { categories ->
            buildList {
                val expense = categories.filter { it.type == TransactionType.EXPENSE }
                val income = categories.filter { it.type == TransactionType.INCOME }
                add(CategoryListItem.Header(R.string.categories_expenses_header))
                expense.forEach { add(CategoryListItem.Entry(it)) }
                add(CategoryListItem.Header(R.string.categories_income_header))
                income.forEach { add(CategoryListItem.Entry(it)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<CategoriesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CategoriesEvent> = _events.asSharedFlow()

    fun create(name: String, type: TransactionType, colorHex: String, iconKey: String) {
        viewModelScope.launch {
            when (repository.addCategory(name, type, colorHex, iconKey)) {
                is CategorySaveResult.Success -> _events.emit(CategoriesEvent.Created)
                CategorySaveResult.DuplicateName -> _events.emit(CategoriesEvent.Duplicate)
            }
        }
    }

    fun update(category: CategoryEntity, name: String, colorHex: String, iconKey: String) {
        viewModelScope.launch {
            when (repository.updateCategory(category.copy(name = name, colorHex = colorHex, iconKey = iconKey))) {
                is CategorySaveResult.Success -> _events.emit(CategoriesEvent.Updated)
                CategorySaveResult.DuplicateName -> _events.emit(CategoriesEvent.Duplicate)
            }
        }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch {
            when (repository.deleteCategory(category.id)) {
                CategoryDeleteResult.DELETED -> _events.emit(CategoriesEvent.Deleted)
                CategoryDeleteResult.IN_USE -> _events.emit(CategoriesEvent.InUse)
                CategoryDeleteResult.NOT_FOUND -> _events.emit(CategoriesEvent.Failed)
            }
        }
    }

    companion object {
        fun factory(repository: ExpenseDataSource): ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel(repository) }
        }
    }
}
