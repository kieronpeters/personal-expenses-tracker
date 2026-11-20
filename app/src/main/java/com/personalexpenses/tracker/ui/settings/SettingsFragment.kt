package com.personalexpenses.tracker.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.databinding.FragmentSettingsBinding
import com.personalexpenses.tracker.ui.common.app
import com.personalexpenses.tracker.ui.common.appPrefs
import com.personalexpenses.tracker.ui.common.repository
import com.personalexpenses.tracker.ui.common.showMessage
import com.personalexpenses.tracker.util.Money
import com.personalexpenses.tracker.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App settings: display currency, category management, CSV export and a
 * destructive "delete all data" action.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding: FragmentSettingsBinding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(app().repository, appPrefs())
    }

    private val createCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val export = viewModel.buildCsv()
            if (export == null) {
                showMessage(R.string.settings_export_empty)
                return@launch
            }
            val success = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(export.content.toByteArray(Charsets.UTF_8))
                        output.flush()
                        true
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                showMessage(getString(R.string.settings_export_done, export.rowCount))
            } else {
                showMessage(R.string.settings_export_failed)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        binding.tvVersion.text = getString(R.string.settings_version, versionName())

        binding.rowCurrency.setOnClickListener { chooseCurrency() }
        binding.rowCategories.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }
        binding.rowExport.setOnClickListener {
            createCsv.launch(getString(R.string.settings_export_file_name))
        }
        binding.rowDeleteAll.setOnClickListener { confirmDeleteAll() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCount()
    }

    private fun render(state: SettingsUiState) {
        binding.tvCurrencyValue.text =
            "${state.currencyCode} (${state.currencySymbol})"
        binding.tvTransactionCount.text = resources.getQuantityString(
            R.plurals.settings_transactions_count,
            state.transactionCount,
            state.transactionCount
        )
    }

    private fun chooseCurrency() {
        val current = appPrefs().currencyCode
        val codes = (Prefs.COMMON_CURRENCIES + current).distinct()
        val labels = codes.map { code ->
            val currency = Money.currencyFor(code)
            "$code (${Money.symbol(currency)})"
        }.toTypedArray()
        val selectedIndex = codes.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_choose_currency)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val code = codes[which]
                dialog.dismiss()
                if (code != current) {
                    viewModel.setCurrency(code)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.settings_currency_changed, code),
                        Toast.LENGTH_SHORT
                    ).show()
                    requireActivity().recreate()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_delete_confirm_title)
            .setMessage(R.string.settings_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.confirm_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteAllData()
                    showMessage(R.string.settings_deleted)
                }
            }
            .show()
    }

    private fun versionName(): String = try {
        val info = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
        info.versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
