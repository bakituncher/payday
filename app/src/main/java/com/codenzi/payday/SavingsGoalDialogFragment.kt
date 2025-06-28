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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

class SavingsGoalDialogFragment : DialogFragment() {

    private val viewModel: PaydayViewModel by activityViewModels()
    private var existingGoal: SavingsGoal? = null
    // Seçilen tarihi milisaniye (Long) olarak tutacak değişken
    private var selectedTimestamp: Long? = null
    // Tarihi formatlamak için yardımcı
    private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goalId = arguments?.getString(ARG_GOAL_ID)
        if (goalId != null) {
            runBlocking {
                existingGoal = viewModel.uiState.value?.savingsGoals?.find { it.id == goalId }
            }
            // Mevcut hedefin tarihini de al
            selectedTimestamp = existingGoal?.targetDate
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_goal_input, null, false)
        val nameEditText = dialogView.findViewById<EditText>(R.id.goalNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.goalAmountEditText)
        val selectDateButton = dialogView.findViewById<Button>(R.id.selectDateButton)
        val selectedDateTextView = dialogView.findViewById<TextView>(R.id.selectedDateTextView)

        val dialogTitle = if (existingGoal == null) {
            getString(R.string.dialog_add_goal_title)
        } else {
            getString(R.string.dialog_edit_goal_title)
        }

        // Mevcut verileri doldur
        existingGoal?.let {
            nameEditText.setText(it.name)
            amountEditText.setText(it.targetAmount.toLong().toString())
        }
        // Mevcut tarih varsa formatlayıp göster
        selectedTimestamp?.let {
            selectedDateTextView.text = dateFormatter.format(Date(it))
        }

        // Tarih Seç Butonuna tıklama olayı
        selectDateButton.setOnClickListener {
            showDatePickerDialog(selectedDateTextView)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (name.isNotBlank() && amount != null && amount > 0) {
                    // ViewModel'a seçilen tarihi de gönder
                    viewModel.addOrUpdateGoal(name, amount, existingGoal?.id, selectedTimestamp)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.toast_invalid_goal_input), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun showDatePickerDialog(dateTextView: TextView) {
        val calendar = Calendar.getInstance()
        // Eğer daha önce bir tarih seçilmişse, takvimi o tarihten başlat
        selectedTimestamp?.let {
            calendar.timeInMillis = it
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // Seçilen tarihi al ve timestamp'e çevir
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                selectedTimestamp = selectedCalendar.timeInMillis
                // Seçilen tarihi formatlayıp TextView'de göster
                dateTextView.text = dateFormatter.format(selectedCalendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Geçmiş bir tarihin seçilmesini engelle
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