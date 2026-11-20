package com.personalexpenses.tracker.ui.transactions

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.snackbar.Snackbar
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.databinding.FragmentTransactionsBinding
import com.personalexpenses.tracker.ui.common.app
import com.personalexpenses.tracker.ui.common.appPrefs
import com.personalexpenses.tracker.ui.common.color
import com.personalexpenses.tracker.ui.common.repository
import com.personalexpenses.tracker.ui.common.setVisible
import com.personalexpenses.tracker.ui.common.themeColor
import com.personalexpenses.tracker.util.Dates
import kotlinx.coroutines.launch

/**
 * Lists the transactions of the selected month with income/expense totals,
 * filters, search, and swipe-to-delete with undo.
 */
class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding: FragmentTransactionsBinding get() = _binding!!

    private val viewModel: TransactionsViewModel by viewModels {
        TransactionsViewModel.factory(app().repository)
    }

    private lateinit var adapter: TransactionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTransactionsBinding.bind(view)

        adapter = TransactionAdapter(
            onClick = { row -> openEditor(row.id) },
            formatMoney = { cents -> appPrefs().formatted(cents) }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
        binding.rvTransactions.setHasFixedSize(false)

        binding.toggleType.check(R.id.btn_filter_all)
        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setTypeFilter(
                when (checkedId) {
                    R.id.btn_filter_expense -> TransactionType.EXPENSE
                    R.id.btn_filter_income -> TransactionType.INCOME
                    else -> null
                }
            )
        }
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.setQuery(text?.toString().orEmpty())
        }
        binding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.currentList.getOrNull(position)
                if (item is TransactionListItem.Entry) {
                    viewModel.delete(item.row)
                    adapter.notifyItemChanged(position)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvTransactions)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: TransactionsUiState) {
        binding.tvMonth.text = Dates.monthLabel(state.monthKey)
        binding.tvSpent.text = appPrefs().formatted(state.totals.expenseCents)
        binding.tvIncome.text = appPrefs().formatted(state.totals.incomeCents)
        binding.tvBalance.text = appPrefs().formatted(state.totals.netCents)
        binding.tvBalance.setTextColor(
            when {
                state.totals.netCents < 0 -> requireContext().color(R.color.expense_red)
                state.totals.netCents > 0 -> requireContext().color(R.color.income_green)
                else -> binding.tvBalance.themeColor(
                    MaterialR.attr.colorOnSurface,
                    requireContext().color(R.color.brand_primary)
                )
            }
        )

        adapter.submitList(state.items)
        val isEmpty = state.items.isEmpty()
        binding.emptyState.setVisible(isEmpty)
        binding.rvTransactions.setVisible(!isEmpty)
        if (isEmpty) {
            val filtered = state.hasActiveFilter && state.monthHasData
            binding.tvEmptyTitle.setText(
                if (filtered) R.string.empty_filtered_title else R.string.empty_transactions_title
            )
            binding.tvEmptySubtitle.setText(
                if (filtered) R.string.empty_filtered_subtitle else R.string.empty_transactions_subtitle
            )
        }
    }

    private fun handleEvent(event: TransactionsEvent) {
        when (event) {
            is TransactionsEvent.Deleted -> Snackbar.make(
                binding.root,
                R.string.transaction_deleted,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.action_undo) {
                viewModel.restore(event.row)
            }.show()

            TransactionsEvent.Restored -> Snackbar.make(
                binding.root,
                R.string.transaction_restored,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun openEditor(transactionId: Long) {
        findNavController().navigate(
            R.id.addEditTransactionFragment,
            bundleOf("transactionId" to transactionId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvTransactions.adapter = null
        _binding = null
    }
}
