package com.codenzi.payday

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.*

class SavingsGoalDialogFragment : DialogFragment() {

    private val viewModel: PaydayViewModel by activityViewModels()
    private var existingGoal: SavingsGoal? = null
    private var selectedTimestamp: Long? = null
    private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    private var selectedCategoryId: Int = SavingsGoalCategory.OTHER.ordinal
    private var selectedPortion: Int = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goalId = arguments?.getString(ARG_GOAL_ID)
        if (goalId != null) {
            existingGoal = viewModel.uiState.value?.savingsGoals?.find { it.id == goalId }
            selectedTimestamp = existingGoal?.targetDate
            selectedCategoryId = existingGoal?.categoryId ?: SavingsGoalCategory.OTHER.ordinal
            selectedPortion = existingGoal?.portion ?: 100
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_goal_input, null, false)
        val nameEditText = dialogView.findViewById<EditText>(R.id.goalNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.goalAmountEditText)
        val selectDateButton = dialogView.findViewById<Button>(R.id.selectDateButton)
        val selectedDateTextView = dialogView.findViewById<TextView>(R.id.selectedDateTextView)
        val categoryChipGroup = dialogView.findViewById<ChipGroup>(R.id.goalCategoryChipGroup)
        val portionSlider = dialogView.findViewById<Slider>(R.id.portionSlider)
        val portionValueTextView = dialogView.findViewById<TextView>(R.id.portionValueTextView)

        // Kategori Chip'lerini oluştur
        SavingsGoalCategory.entries.forEach { category ->
            val chip = Chip(requireContext()).apply {
                // HATA BURADAYDI: DÜZELTİLDİ
                text = category.getDisplayName(requireContext())
                id = category.ordinal
                isCheckable = true
                isChecked = (id == selectedCategoryId)
            }
            categoryChipGroup.addView(chip)
        }
        categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedCategoryId = checkedIds.first()
            }
        }

        portionSlider.value = selectedPortion.toFloat()
        portionValueTextView.text = getString(R.string.portion_percentage, selectedPortion)
        portionSlider.addOnChangeListener { _, value, _ ->
            selectedPortion = value.toInt()
            portionValueTextView.text = getString(R.string.portion_percentage, selectedPortion)
        }

        val dialogTitle = if (existingGoal == null) {
            getString(R.string.dialog_add_goal_title)
        } else {
            getString(R.string.dialog_edit_goal_title)
        }

        existingGoal?.let {
            nameEditText.setText(it.name)
            amountEditText.setText(it.targetAmount.toLong().toString())
        }
        selectedTimestamp?.let {
            selectedDateTextView.text = dateFormatter.format(Date(it))
        }

        selectDateButton.setOnClickListener {
            showDatePickerDialog(selectedDateTextView)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()
                val currentGoals = viewModel.uiState.value?.savingsGoals ?: emptyList()
                val otherGoalsPortion = currentGoals.filter { it.id != existingGoal?.id }.sumOf { it.portion }
                val totalPortion = otherGoalsPortion + selectedPortion

                if (totalPortion > 100) {
                    Toast.makeText(requireContext(), getString(R.string.toast_total_portion_exceeded), Toast.LENGTH_LONG).show()
                } else if (name.isNotBlank() && amount != null && amount > 0) {
                    viewModel.addOrUpdateGoal(name, amount, existingGoal?.id, selectedTimestamp, selectedCategoryId, selectedPortion)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.toast_invalid_goal_input), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun showDatePickerDialog(dateTextView: TextView) {
        val calendar = Calendar.getInstance()
        selectedTimestamp?.let {
            calendar.timeInMillis = it
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                selectedTimestamp = selectedCalendar.timeInMillis
                dateTextView.text = dateFormatter.format(selectedCalendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    companion object {
        const val TAG = "SavingsGoalDialog"
        private const val ARG_GOAL_ID = "goal_id"

        fun newInstance(goalId: String?): SavingsGoalDialogFragment {
            val fragment = SavingsGoalDialogFragment()
            if (goalId != null) {
                fragment.arguments = bundleOf(ARG_GOAL_ID to goalId)
            }
            return fragment
        }
    }
}