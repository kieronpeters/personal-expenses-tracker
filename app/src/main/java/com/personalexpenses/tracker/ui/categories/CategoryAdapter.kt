package com.personalexpenses.tracker.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.databinding.ItemCategoryBinding
import com.personalexpenses.tracker.databinding.ItemDateHeaderBinding
import com.personalexpenses.tracker.ui.common.CategoryBadge
import com.personalexpenses.tracker.ui.common.themeColor

/**
 * Adapter for the category manager, rendering section headings for expense and
 * income categories.
 */
class CategoryAdapter(
    private val onEdit: (CategoryEntity) -> Unit,
    private val onDelete: (CategoryEntity) -> Unit
) : ListAdapter<CategoryListItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CategoryListItem.Header -> TYPE_HEADER
        is CategoryListItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryViewHolder(ItemCategoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as CategoryListItem.Header)
            is EntryViewHolder -> holder.bind(getItem(position) as CategoryListItem.Entry)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryListItem.Header) {
            binding.tvDateHeader.setText(item.titleRes)
        }
    }

    private inner class EntryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryListItem.Entry) {
            val category = item.category
            val context = binding.root.context
            CategoryBadge.apply(binding.tvCategoryIcon, category.iconKey, category.colorHex)
            binding.tvCategoryName.text = category.name

            val typeLabel = context.getString(
                if (category.type == TransactionType.EXPENSE) R.string.type_expense
                else R.string.type_income
            )
            binding.tvCategoryType.text = if (category.isDefault) {
                "$typeLabel \u00b7 ${context.getString(R.string.category_default)}"
            } else {
                typeLabel
            }
            binding.root.contentDescription = "${category.name}, $typeLabel"
            binding.root.setOnClickListener { onEdit(category) }
            binding.btnEdit.setOnClickListener { onEdit(category) }
            binding.btnDelete.setOnClickListener { onDelete(category) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ENTRY = 1
    }

    object Diff : DiffUtil.ItemCallback<CategoryListItem>() {
        override fun areItemsTheSame(oldItem: CategoryListItem, newItem: CategoryListItem): Boolean =
            when {
                oldItem is CategoryListItem.Header && newItem is CategoryListItem.Header ->
                    oldItem.titleRes == newItem.titleRes
                oldItem is CategoryListItem.Entry && newItem is CategoryListItem.Entry ->
                    oldItem.category.id == newItem.category.id
                else -> false
            }

        override fun areContentsTheSame(oldItem: CategoryListItem, newItem: CategoryListItem): Boolean =
            oldItem == newItem
    }
}
