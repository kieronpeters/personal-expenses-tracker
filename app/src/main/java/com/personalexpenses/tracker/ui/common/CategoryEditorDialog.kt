package com.personalexpenses.tracker.ui.common

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.data.SeedData
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.model.TransactionType
import com.personalexpenses.tracker.databinding.DialogCategoryBinding

/**
 * Shared create/edit category dialog used by the category manager and by the
 * "new category" entry in the transaction editor.
 */
object CategoryEditorDialog {

    val COLORS: List<String> = listOf(
        "#EF4444", "#F97316", "#F59E0B", "#84CC16", "#10B981", "#14B8A6",
        "#06B6D4", "#3B82F6", "#6366F1", "#8B5CF6", "#EC4899", "#6B7280"
    )

    val ICONS: List<String> = listOf(
        "\uD83C\uDF54", "\uD83D\uDED2", "\uD83D\uDE8C", "\uD83C\uDFE0", "\uD83D\uDCA1",
        "\uD83D\uDECD\uFE0F", "\uD83D\uDC8A", "\uD83C\uDFAC", "\uD83D\uDCDA", "\u2708\uFE0F",
        "\uD83D\uDCB0", "\uD83D\uDCBB", "\uD83D\uDCC8", "\uD83C\uDF81", SeedData.DEFAULT_ICON,
        "\u2615", "\u26BD", "\uD83D\uDC3E", "\uD83C\uDFB5", "\u2795"
    )

    fun show(
        context: Context,
        existing: CategoryEntity?,
        defaultType: TransactionType = TransactionType.EXPENSE,
        onSave: (name: String, type: TransactionType, colorHex: String, iconKey: String) -> Unit
    ) {
        val binding = DialogCategoryBinding.inflate(LayoutInflater.from(context))
        var selectedType = existing?.type ?: defaultType
        var selectedColor = existing?.colorHex ?: COLORS.first()
        var selectedIcon = existing?.iconKey ?: SeedData.DEFAULT_ICON

        binding.toggleCategoryType.setVisible(existing == null)
        if (existing == null) {
            binding.toggleCategoryType.check(
                if (selectedType == TransactionType.EXPENSE) R.id.btn_type_expense
                else R.id.btn_type_income
            )
            binding.toggleCategoryType.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                selectedType = if (checkedId == R.id.btn_type_income) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
            }
        }

        binding.etName.setText(existing?.name.orEmpty())

        binding.etIcon.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, ICONS)
        )
        binding.etIcon.setText(selectedIcon, false)
        binding.etIcon.setOnItemClickListener { _, _, position, _ ->
            selectedIcon = ICONS[position]
            binding.etIcon.setText(selectedIcon, false)
        }

        val swatches = mutableListOf<TextView>()
        val refreshSwatches = {
            swatches.forEachIndexed { index, view ->
                val hex = COLORS[index]
                view.text = if (hex == selectedColor) "\u2713" else ""
                (view.background as? GradientDrawable)?.setStroke(
                    context.dp(if (hex == selectedColor) 3 else 0),
                    Color.WHITE
                )
            }
        }

        COLORS.forEach { hex ->
            val swatch = TextView(context).apply {
                text = ""
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply {
                    marginEnd = context.dp(8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
                setOnClickListener {
                    selectedColor = hex
                    refreshSwatches()
                }
            }
            swatches += swatch
            binding.colorContainer.addView(swatch)
        }
        refreshSwatches()

        val dialog: AlertDialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.category_new else R.string.category_edit)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    binding.nameLayout.error = context.getString(R.string.category_name_required)
                    return@setOnClickListener
                }
                binding.nameLayout.error = null
                onSave(name, selectedType, selectedColor, selectedIcon)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Returns the view that should receive focus when the dialog opens. */
    fun focusView(binding: DialogCategoryBinding): View = binding.etName
}
