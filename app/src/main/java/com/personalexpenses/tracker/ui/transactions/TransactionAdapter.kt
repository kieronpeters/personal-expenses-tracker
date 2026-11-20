package com.personalexpenses.tracker.ui.transactions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.databinding.ItemDateHeaderBinding
import com.personalexpenses.tracker.databinding.ItemTransactionBinding
import com.personalexpenses.tracker.ui.common.CategoryBadge
import com.personalexpenses.tracker.ui.common.color
import com.personalexpenses.tracker.ui.common.setVisible
import com.personalexpenses.tracker.ui.common.themeColor

/**
 * RecyclerView adapter that renders the day-grouped transaction list.
 * Headers and transactions share one adapter so the whole list scrolls as a
 * unit and DiffUtil animates inserts and removals.
 */
class TransactionAdapter(
    private val onClick: (TransactionRow) -> Unit,
    private val formatMoney: (Long) -> String
) : ListAdapter<TransactionListItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TransactionListItem.DateHeader -> TYPE_HEADER
        is TransactionListItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
            else -> EntryViewHolder(ItemTransactionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as TransactionListItem.DateHeader)
            is EntryViewHolder -> holder.bind(getItem(position) as TransactionListItem.Entry)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionListItem.DateHeader) {
            binding.tvDateHeader.text = item.label
        }
    }

    private inner class EntryViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionListItem.Entry) {
            val row = item.row
            val context = binding.root.context
            binding.tvCategoryName.text = row.categoryName
            binding.tvNote.text = row.note
            binding.tvNote.setVisible(row.note.isNotBlank())
            CategoryBadge.apply(binding.tvCategoryIcon, row.categoryIcon, row.categoryColor)

            val isIncome = row.type == TransactionType.INCOME
            val sign = if (isIncome) "+" else "\u2212"
            binding.tvAmount.text = "$sign${formatMoney(row.amountCents)}"
            binding.tvAmount.setTextColor(
                context.color(if (isIncome) R.color.income_green else R.color.expense_red)
            )
            binding.root.contentDescription = buildString {
                append(row.categoryName)
                if (row.note.isNotBlank()) append(", ").append(row.note)
                append(", ").append(if (isIncome) "income " else "expense ")
                append(formatMoney(row.amountCents))
            }
            binding.root.setOnClickListener { onClick(row) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ENTRY = 1
    }

    object Diff : DiffUtil.ItemCallback<TransactionListItem>() {
        override fun areItemsTheSame(oldItem: TransactionListItem, newItem: TransactionListItem): Boolean =
            when {
                oldItem is TransactionListItem.DateHeader && newItem is TransactionListItem.DateHeader ->
                    oldItem.label == newItem.label
                oldItem is TransactionListItem.Entry && newItem is TransactionListItem.Entry ->
                    oldItem.row.id == newItem.row.id
                else -> false
            }

        override fun areContentsTheSame(oldItem: TransactionListItem, newItem: TransactionListItem): Boolean =
            oldItem == newItem
    }
}
