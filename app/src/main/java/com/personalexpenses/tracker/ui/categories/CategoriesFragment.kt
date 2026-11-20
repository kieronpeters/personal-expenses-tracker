package com.personalexpenses.tracker.ui.categories

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.databinding.FragmentCategoriesBinding
import com.personalexpenses.tracker.ui.common.CategoryEditorDialog
import com.personalexpenses.tracker.ui.common.app
import com.personalexpenses.tracker.ui.common.repository
import com.personalexpenses.tracker.ui.common.showMessage
import kotlinx.coroutines.launch

/**
 * Lists every category and lets the user add, rename, restyle or delete them.
 * Categories that are still referenced by transactions cannot be deleted.
 */
class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding: FragmentCategoriesBinding get() = _binding!!

    private val viewModel: CategoriesViewModel by viewModels {
        CategoriesViewModel.factory(app().repository)
    }

    private lateinit var adapter: CategoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCategoriesBinding.bind(view)

        adapter = CategoryAdapter(
            onEdit = { category -> showEditor(category) },
            onDelete = { category -> confirmDelete(category) }
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
        binding.fabAddCategory.setOnClickListener { showEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.items.collect { adapter.submitList(it) } }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun showEditor(existing: CategoryEntity?) {
        CategoryEditorDialog.show(
            context = requireContext(),
            existing = existing
        ) { name, type, colorHex, iconKey ->
            if (existing == null) {
                viewModel.create(name, type, colorHex, iconKey)
            } else {
                viewModel.update(existing, name, colorHex, iconKey)
            }
        }
    }

    private fun confirmDelete(category: CategoryEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_category_confirm_title)
            .setMessage(R.string.delete_category_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(category) }
            .show()
    }

    private fun handleEvent(event: CategoriesEvent) {
        when (event) {
            CategoriesEvent.Created -> showMessage(R.string.category_added)
            CategoriesEvent.Updated -> showMessage(R.string.category_updated)
            CategoriesEvent.Duplicate -> showMessage(R.string.category_duplicate)
            CategoriesEvent.Deleted -> showMessage(R.string.category_deleted)
            CategoriesEvent.InUse -> showMessage(R.string.category_in_use)
            CategoriesEvent.Failed -> showMessage(R.string.category_deleted)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCategories.adapter = null
        _binding = null
    }
}
