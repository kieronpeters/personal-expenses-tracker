package com.personalexpenses.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personalexpenses.tracker.data.ExpenseDataSource
import com.personalexpenses.tracker.util.CsvExporter
import com.personalexpenses.tracker.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val transactionCount: Int = 0,
    val currencyCode: String = "USD",
    val currencySymbol: String = "$"
)

data class CsvExport(val content: String, val rowCount: Int)

/** Backs the settings screen: currency, data maintenance and CSV export. */
class SettingsViewModel(
    private val repository: ExpenseDataSource,
    private val prefs: Prefs
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            currencyCode = prefs.currencyCode,
            currencySymbol = prefs.currencySymbol()
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshCount()
    }

    fun refreshCount() {
        viewModelScope.launch {
            _state.update { it.copy(transactionCount = repository.countTransactions()) }
        }
    }

    fun setCurrency(code: String) {
        prefs.currencyCode = code
        _state.update {
            it.copy(currencyCode = code, currencySymbol = prefs.currencySymbol())
        }
    }

    suspend fun deleteAllData() {
        repository.deleteAllData()
        _state.update { it.copy(transactionCount = 0) }
    }

    suspend fun buildCsv(): CsvExport? {
        val rows = repository.getAllTransactions()
        if (rows.isEmpty()) return null
        val content = CsvExporter.build(rows, prefs.currencyCode, prefs.fractionDigits())
        return CsvExport(content, rows.size)
    }

    companion object {
        fun factory(repository: ExpenseDataSource, prefs: Prefs): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SettingsViewModel(repository, prefs) }
            }
    }
}
