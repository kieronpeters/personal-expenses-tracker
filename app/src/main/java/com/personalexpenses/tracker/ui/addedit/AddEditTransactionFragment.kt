package com.personalexpenses.tracker.ui.addedit

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.databinding.FragmentAddEditTransactionBinding
import com.personalexpenses.tracker.ui.MainActivity
import com.personalexpenses.tracker.ui.common.CategoryEditorDialog
import com.personalexpenses.tracker.ui.common.app
import com.personalexpenses.tracker.ui.common.appPrefs
import com.personalexpenses.tracker.ui.common.repository
import com.personalexpenses.tracker.ui.common.setVisible
import com.personalexpenses.tracker.ui.common.showMessage
import com.personalexpenses.tracker.util.Dates
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Form for creating and editing a transaction. Handles amount parsing,
 * category selection (including inline creation), date picking and validation.
 */
class AddEditTransactionFragment : Fragment(R.layout.fragment_add_edit_transaction) {

    private var _binding: FragmentAddEditTransactionBinding? = null
    private val binding: FragmentAddEditTransactionBinding get() = _binding!!

    private val transactionId: Long by lazy { requireArguments().getLong("transactionId", 0L) }

    private val viewModel: AddEditTransactionViewModel by viewModels {
        AddEditTransactionViewModel.factory(
            repository = app().repository,
            transactionId = transactionId,
            fractionDigits = appPrefs().fractionDigits()
        )
    }

    private var categories: List<CategoryEntity> = emptyList()
    private var updatingUi = false
    private var leftScreen = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAddEditTransactionBinding.bind(view)

        (requireActivity() as? MainActivity)?.supportActionBar?.title = getString(
            if (viewModel.isEdit) R.string.title_edit_transaction else R.string.title_add_transaction
        )

        binding.amountLayout.prefixText = appPrefs().currencySymbol()
        binding.btnDelete.setVisible(viewModel.isEdit)

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingUi) return@addOnButtonCheckedListener
            viewModel.onTypeChanged(
                if (checkedId == R.id.btn_income) TransactionType.INCOME else TransactionType.EXPENSE
            )
        }
        binding.etAmount.doAfterTextChanged { text ->
            if (!updatingUi) viewModel.onAmountChanged(text?.toString().orEmpty())
        }
        binding.etNote.doAfterTextChanged { text ->
            if (!updatingUi) viewModel.onNoteChanged(text?.toString().orEmpty())
        }
        binding.etDate.setOnClickListener { openDatePicker() }
        binding.dateLayout.setOnClickListener { openDatePicker() }
        binding.dateLayout.setEndIconOnClickListener { openDatePicker() }

        binding.etCategory.setOnItemClickListener { _, _, position, _ ->
            val names = categoryNames(categories)
            when {
                position == names.size -> promptNewCategory()
                position in categories.indices -> viewModel.onCategorySelected(categories[position].id)
            }
        }

        binding.btnSave.setOnClickListener {
            viewModel.onAmountChanged(binding.etAmount.text?.toString().orEmpty())
            viewModel.onNoteChanged(binding.etNote.text?.toString().orEmpty())
            viewModel.save()
        }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: AddEditUiState) {
        // Type switch (guarded against feedback loops).
        val targetButton = if (state.type == TransactionType.INCOME) {
            R.id.btn_income
        } else {
            R.id.btn_expense
        }
        if (binding.toggleType.checkedButtonId != targetButton) {
            updatingUi = true
            binding.toggleType.check(targetButton)
            updatingUi = false
        }

        if (binding.etAmount.text?.toString() != state.amountText) {
            updatingUi = true
            binding.etAmount.setText(state.amountText)
            binding.etAmount.setSelection(state.amountText.length)
            updatingUi = false
        }
        if (binding.etNote.text?.toString() != state.note) {
            updatingUi = true
            binding.etNote.setText(state.note)
            updatingUi = false
        }

        categories = state.categories
        refreshCategoryAdapter()
        val selectedName = state.selectedCategory
            ?.let { "${it.iconKey}  ${it.name}" }
            .orEmpty()
        if (binding.etCategory.text?.toString() != selectedName) {
            binding.etCategory.setText(selectedName, false)
        }

        binding.etDate.setText(Dates.dateLabel(state.date))

        binding.amountLayout.error = state.amountError?.let { getString(it) }
        binding.categoryLayout.error = state.categoryError?.let { getString(it) }
        binding.btnSave.isEnabled = !state.saving

        if (state.saveError) {
            showMessage(R.string.error_save_failed)
        }
        if (state.notFound && !leftScreen) {
            leftScreen = true
            showMessage(R.string.transaction_not_found)
            findNavController().popBackStack()
        }
        if ((state.saved || state.deleted) && !leftScreen) {
            leftScreen = true
            findNavController().popBackStack()
        }
    }

    private fun refreshCategoryAdapter() {
        val names = categoryNames(categories)
        val existing = binding.etCategory.adapter
        if (existing == null || existing.count != names.size) {
            binding.etCategory.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
            )
        } else {
            @Suppress("UNCHECKED_CAST")
            val adapter = existing as ArrayAdapter<String>
            adapter.clear()
            adapter.addAll(names)
            adapter.notifyDataSetChanged()
        }
    }

    private fun categoryNames(list: List<CategoryEntity>): List<String> =
        list.map { "${it.iconKey}  ${it.name}" } + getString(R.string.action_new_category)

    private fun openDatePicker() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = viewModel.state.value.date
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.onDateChanged(selected.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun promptNewCategory() {
        val type = viewModel.state.value.type
        CategoryEditorDialog.show(
            context = requireContext(),
            existing = null,
            defaultType = type
        ) { name, categoryType, colorHex, iconKey ->
            viewModel.createCategory(name, categoryType, colorHex, iconKey)
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.confirm_delete) { _, _ -> viewModel.delete() }
            .show()
    }

    private fun handleEvent(event: AddEditEvent) {
        when (event) {
            is AddEditEvent.CategoryCreated -> showMessage(R.string.category_added)
            AddEditEvent.CategoryDuplicate -> showMessage(R.string.error_category_duplicate)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
