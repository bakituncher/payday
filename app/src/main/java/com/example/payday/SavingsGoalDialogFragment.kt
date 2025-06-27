package com.example.payday

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SavingsGoalDialogFragment : DialogFragment() {

    private val viewModel: PaydayViewModel by activityViewModels()
    private var existingGoal: SavingsGoal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goalId = arguments?.getString(ARG_GOAL_ID)
        if (goalId != null) {
            // ViewModel'dan ilgili hedefi anlık olarak bul
            // Not: runBlocking burada sadece ilk kurulum için kullanılıyor, idealde bu da asenkron olmalı
            // ancak dialog yapısı için bu pratik bir çözümdür.
            runBlocking {
                existingGoal = viewModel.uiState.value?.savingsGoals?.find { it.id == goalId }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_goal_input, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.goalNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.goalAmountEditText)

        val dialogTitle = if (existingGoal == null) {
            getString(R.string.dialog_add_goal_title)
        } else {
            getString(R.string.dialog_edit_goal_title)
        }

        existingGoal?.let {
            nameEditText.setText(it.name)
            amountEditText.setText(it.targetAmount.toLong().toString())
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (name.isNotBlank() && amount != null && amount > 0) {
                    viewModel.addOrUpdateGoal(name, amount, existingGoal?.id)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.toast_invalid_goal_input), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    companion object {
        const val TAG = "SavingsGoalDialog"
        private const val ARG_GOAL_ID = "goal_id"

        // newInstance metodu artık String? (ID) alacak
        fun newInstance(goalId: String?): SavingsGoalDialogFragment {
            val fragment = SavingsGoalDialogFragment()
            if (goalId != null) {
                fragment.arguments = bundleOf(ARG_GOAL_ID to goalId)
            }
            return fragment
        }
    }
}