package com.personalexpenses.tracker.ui.budgets

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.BudgetProgress
import com.personalexpenses.tracker.databinding.FragmentBudgetsBinding
import com.personalexpenses.tracker.databinding.ItemBudgetBinding
import com.personalexpenses.tracker.databinding.DialogBudgetBinding
import com.personalexpenses.tracker.ui.common.CategoryBadge
import com.personalexpenses.tracker.ui.common.app
import com.personalexpenses.tracker.ui.common.appPrefs
import com.personalexpenses.tracker.ui.common.color
import com.personalexpenses.tracker.ui.common.repository
import com.personalexpenses.tracker.ui.common.setVisible
import com.personalexpenses.tracker.ui.common.showMessage
import com.personalexpenses.tracker.util.Dates
import com.personalexpenses.tracker.util.Money
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shows the overall budget and per-category budgets for the selected month,
 * together with the actual spending of that month.
 */
class BudgetsFragment : Fragment(R.layout.fragment_budgets) {

    private var _binding: FragmentBudgetsBinding? = null
    private val binding: FragmentBudgetsBinding get() = _binding!!

    private val viewModel: BudgetsViewModel by viewModels {
        BudgetsViewModel.factory(app().repository)
    }

    private var lastState: BudgetsUiState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBudgetsBinding.bind(view)

        binding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }
        binding.btnEditOverall.setOnClickListener {
            showBudgetDialog(lastState?.overall, forceOverall = true)
        }
        binding.btnAddCategoryBudget.setOnClickListener { showBudgetDialog(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: BudgetsUiState) {
        lastState = state
        val prefs = appPrefs()
        binding.tvMonth.text = Dates.monthLabel(state.monthKey)

        binding.tvOverallAmount.text = prefs.formatted(state.totalExpenseCents)
        val overall = state.overall
        binding.progressOverall.setVisible(overall != null)
        if (overall != null) {
            binding.tvOverallStatus.text = getString(
                R.string.budget_limit_label,
                prefs.formatted(overall.budgetCents)
            )
            binding.progressOverall.progress =
                (overall.usedFraction * 100f).roundToInt().coerceIn(0, 100)
            val over = overall.isOverBudget
            binding.progressOverall.setIndicatorColor(
                requireContext().color(if (over) R.color.expense_red else R.color.brand_primary)
            )
            binding.tvOverallDetail.text = if (over) {
                getString(R.string.budget_over, prefs.formatted(abs(overall.remainingCents)))
            } else {
                getString(R.string.budget_remaining, prefs.formatted(overall.remainingCents))
            }
            binding.btnEditOverall.setText(R.string.budget_edit)
        } else {
            binding.tvOverallStatus.setText(R.string.budget_no_overall)
            binding.tvOverallDetail.text = ""
            binding.btnEditOverall.setText(R.string.budget_set)
        }

        renderCategoryBudgets(state.categoryBudgets)
        binding.emptyBudgets.setVisible(state.categoryBudgets.isEmpty())
        binding.btnAddCategoryBudget.isEnabled = state.availableCategories.isNotEmpty()
        binding.tvUnbudgeted.setVisible(state.unbudgetedSpendCents > 0L)
        if (state.unbudgetedSpendCents > 0L) {
            binding.tvUnbudgeted.text = getString(
                R.string.unbudgeted_spending,
                prefs.formatted(state.unbudgetedSpendCents)
            )
        }
    }

    private fun renderCategoryBudgets(budgets: List<BudgetProgress>) {
        val prefs = appPrefs()
        binding.listCategoryBudgets.removeAllViews()
        budgets.forEach { progress ->
            val item = ItemBudgetBinding.inflate(layoutInflater, binding.listCategoryBudgets, false)
            CategoryBadge.apply(item.tvCategoryIcon, progress.categoryIcon, progress.categoryColor)
            item.tvCategoryName.text = progress.categoryName
            item.tvAmounts.text = getString(
                R.string.budget_spent_of,
                prefs.formatted(progress.spentCents),
                prefs.formatted(progress.budgetCents)
            )
            item.progress.progress = (progress.usedFraction * 100f).roundToInt().coerceIn(0, 100)
            item.progress.setIndicatorColor(
                requireContext().color(
                    if (progress.isOverBudget) R.color.expense_red else R.color.brand_primary
                )
            )
            item.tvStatus.text = if (progress.isOverBudget) {
                getString(R.string.budget_over, prefs.formatted(abs(progress.remainingCents)))
            } else {
                getString(R.string.budget_used_percent, progress.usedPercent) +
                    " \u00b7 " + getString(
                        R.string.budget_remaining,
                        prefs.formatted(progress.remainingCents)
                    )
            }
            item.root.setOnClickListener { showBudgetDialog(progress) }
            item.btnMore.setOnClickListener { showRowMenu(it, progress) }
            binding.listCategoryBudgets.addView(item.root)
        }
    }

    private fun showRowMenu(anchor: View, progress: BudgetProgress) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_EDIT, 0, R.string.edit)
        popup.menu.add(0, MENU_DELETE, 1, R.string.remove)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> showBudgetDialog(progress)
                MENU_DELETE -> confirmRemove(progress)
            }
            true
        }
        popup.show()
    }

    private fun confirmRemove(progress: BudgetProgress) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (progress.isOverall) R.string.budget_overall_title
                else R.string.budget_dialog_title_category
            )
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                viewModel.removeBudget(progress.categoryId)
            }
            .show()
    }

    private fun showBudgetDialog(existing: BudgetProgress?, forceOverall: Boolean = false) {
        val state = lastState ?: return
        val prefs = appPrefs()
        val dialogBinding = DialogBudgetBinding.inflate(layoutInflater)
        dialogBinding.amountLayout.prefixText = prefs.currencySymbol()

        val isOverall = forceOverall || existing?.isOverall == true
        val fixedCategoryId: Long?
        var selectedCategoryId: Long? = null

        when {
            isOverall -> {
                fixedCategoryId = BudgetProgress.OVERALL_CATEGORY_ID
                dialogBinding.categoryLayout.isVisible = false
                if (existing != null) {
                    dialogBinding.etAmount.setText(
                        Money.toInputString(existing.budgetCents, prefs.fractionDigits())
                    )
                }
            }

            existing != null -> {
                fixedCategoryId = existing.categoryId
                dialogBinding.categoryLayout.isVisible = false
                val category = state.allExpenseCategories.firstOrNull {
                    it.id == existing.categoryId
                }
                dialogBinding.categoryLayout.hint = category?.name
                dialogBinding.etCategory.setText(category?.name.orEmpty(), false)
                dialogBinding.etAmount.setText(
                    Money.toInputString(existing.budgetCents, prefs.fractionDigits())
                )
            }

            else -> {
                val available = state.availableCategories
                if (available.isEmpty()) return
                fixedCategoryId = null
                val names = available.map { "${it.iconKey}  ${it.name}" }
                dialogBinding.etCategory.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                )
                dialogBinding.etCategory.setText(names.first(), false)
                selectedCategoryId = available.first().id
                dialogBinding.etCategory.setOnItemClickListener { _, _, position, _ ->
                    selectedCategoryId = available.getOrNull(position)?.id
                }
            }
        }

        val title = if (isOverall) {
            R.string.budget_dialog_title_overall
        } else {
            R.string.budget_dialog_title_category
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)

        if (existing != null) {
            builder.setNeutralButton(R.string.budget_dialog_remove, null)
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val cents = Money.parseToCents(
                    dialogBinding.etAmount.text?.toString().orEmpty(),
                    prefs.fractionDigits()
                )
                if (cents == null || cents <= 0L) {
                    dialogBinding.amountLayout.error = getString(R.string.error_invalid_budget)
                    return@setOnClickListener
                }
                val categoryId = fixedCategoryId ?: selectedCategoryId
                if (categoryId == null) {
                    return@setOnClickListener
                }
                dialogBinding.amountLayout.error = null
                viewModel.setBudget(categoryId, cents)
                dialog.dismiss()
            }
            if (existing != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    dialog.dismiss()
                    confirmRemove(existing)
                }
            }
        }
        dialog.show()
    }

    private fun handleEvent(event: BudgetsEvent) {
        when (event) {
            BudgetsEvent.Saved -> showMessage(R.string.budget_saved)
            BudgetsEvent.Removed -> showMessage(R.string.budget_removed)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.listCategoryBudgets.removeAllViews()
        _binding = null
    }

    private companion object {
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }
}
