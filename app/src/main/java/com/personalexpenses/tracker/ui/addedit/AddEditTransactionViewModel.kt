package com.personalexpenses.tracker.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.CategorySaveResult
import com.personalexpenses.tracker.data.model.TransactionDraft
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.util.Dates
import com.personalexpenses.tracker.util.Money
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditUiState(
    val isEdit: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val date: Long = 0L,
    val note: String = "",
    val allCategories: List<CategoryEntity> = emptyList(),
    val amountError: Int? = null,
    val categoryError: Int? = null,
    val saveError: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val notFound: Boolean = false
) {
    fun categoriesFor(type: TransactionType): List<CategoryEntity> =
        allCategories.filter { it.type == type }

    val categories: List<CategoryEntity> get() = categoriesFor(type)

    val selectedCategory: CategoryEntity?
        get() = allCategories.firstOrNull { it.id == categoryId }
}

sealed interface AddEditEvent {
    data class CategoryCreated(val categoryId: Long) : AddEditEvent

    data object CategoryDuplicate : AddEditEvent
}

/**
 * Backs both "add" and "edit" transaction modes. The same state and validation
 * logic is reused; only the persisted id and the initial values differ.
 */
class AddEditTransactionViewModel(
    private val repository: ExpenseDataSource,
    private val transactionId: Long,
    private val fractionDigits: Int,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    val isEdit: Boolean = transactionId > 0L

    private val _state = MutableStateFlow(
        AddEditUiState(
            isEdit = isEdit,
            date = Dates.startOfDay(now())
        )
    )
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddEditEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddEditEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeCategories().collect { categories ->
                _state.update { current ->
                    val options = categories.filter { it.type == current.type }
                    val stillValid = current.categoryId?.let { id -> options.any { it.id == id } } == true
                    current.copy(
                        allCategories = categories,
                        categoryId = if (stillValid) current.categoryId else options.firstOrNull()?.id
                    )
                }
            }
        }
        if (isEdit) {
            viewModelScope.launch {
                val row = repository.getTransactionRow(transactionId)
                if (row == null) {
                    _state.update { it.copy(notFound = true) }
                } else {
                    _state.update {
                        it.copy(
                            amountText = Money.toInputString(row.amountCents, fractionDigits),
                            type = row.type,
                            categoryId = row.categoryId,
                            date = Dates.startOfDay(row.date),
                            note = row.note
                        )
                    }
                }
            }
        }
    }

    fun onAmountChanged(text: String) {
        _state.update { it.copy(amountText = text, amountError = null) }
    }

    fun onTypeChanged(type: TransactionType) {
        if (_state.value.type == type) return
        _state.update { current ->
            current.copy(
                type = type,
                categoryId = current.categoriesFor(type).firstOrNull()?.id,
                categoryError = null
            )
        }
    }

    fun onCategorySelected(categoryId: Long) {
        _state.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onDateChanged(millis: Long) {
        _state.update { it.copy(date = Dates.startOfDay(millis)) }
    }

    fun onNoteChanged(text: String) {
        _state.update { it.copy(note = text) }
    }

    fun save() {
        val snapshot = _state.value
        val cents = Money.parseToCents(snapshot.amountText, fractionDigits)
        val category = snapshot.categories.firstOrNull { it.id == snapshot.categoryId }
        val amountError = if (cents == null || cents <= 0L) R.string.error_invalid_amount else null
        val categoryError = if (category == null) R.string.error_category_required else null
        if (amountError != null || categoryError != null) {
            _state.update { it.copy(amountError = amountError, categoryError = categoryError) }
            return
        }
        val draft = TransactionDraft(
            amountCents = cents!!,
            type = snapshot.type,
            categoryId = category!!.id,
            date = Dates.startOfDay(snapshot.date),
            note = snapshot.note.trim()
        )
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = false) }
            try {
                if (isEdit) {
                    repository.updateTransaction(transactionId, draft)
                } else {
                    repository.addTransaction(draft)
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = true) }
            }
        }
    }

    fun delete() {
        if (!isEdit) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = false) }
            try {
                repository.deleteTransaction(transactionId)
                _state.update { it.copy(saving = false, deleted = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = true) }
            }
        }
    }

    fun createCategory(name: String, type: TransactionType, colorHex: String, iconKey: String) {
        viewModelScope.launch {
            when (val result = repository.addCategory(name, type, colorHex, iconKey)) {
                is CategorySaveResult.Success -> {
                    _state.update { it.copy(type = type, categoryId = result.id) }
                    _events.emit(AddEditEvent.CategoryCreated(result.id))
                }

                CategorySaveResult.DuplicateName ->
                    _events.emit(AddEditEvent.CategoryDuplicate)
            }
        }
    }

    companion object {
        fun factory(
            repository: ExpenseDataSource,
            transactionId: Long,
            fractionDigits: Int
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddEditTransactionViewModel(repository, transactionId, fractionDigits)
            }
        }
    }
}
