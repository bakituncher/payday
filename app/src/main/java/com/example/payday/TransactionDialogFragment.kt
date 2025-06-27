package com.example.payday

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TransactionDialogFragment : DialogFragment() {

    private val viewModel: PaydayViewModel by activityViewModels()
    private var existingTransaction: Transaction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transactionId = arguments?.getInt(ARG_TRANSACTION_ID, -1) ?: -1
        if (transactionId != -1) {
            existingTransaction = viewModel.allTransactions.value?.find { it.id == transactionId }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_input, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.transactionNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.transactionAmountEditText)
        val categoryChipGroup = dialogView.findViewById<ChipGroup>(R.id.categoryChipGroup)
        var selectedCategoryId = existingTransaction?.categoryId ?: ExpenseCategory.OTHER.ordinal

        // *** DEĞİŞİKLİK BURADA: .values() yerine .entries kullanıldı ***
        ExpenseCategory.entries.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category.categoryName
                id = category.ordinal
                isCheckable = true
                isChecked = (id == selectedCategoryId)
            }
            categoryChipGroup.addView(chip)
        }

        categoryChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedCategoryId = checkedIds.first()
            }
        }

        existingTransaction?.let {
            nameEditText.setText(it.name)
            amountEditText.setText(it.amount.toString())
        }

        val dialogTitleRes = if (existingTransaction == null) R.string.add_transaction else R.string.edit_transaction_title

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (name.isNotBlank() && amount != null && amount > 0) {
                    if (existingTransaction == null) {
                        viewModel.insertTransaction(name, amount, selectedCategoryId)
                    } else {
                        viewModel.updateTransaction(existingTransaction!!.id, name, amount, selectedCategoryId)
                    }
                } else {
                    Toast.makeText(requireContext(), "Lütfen geçerli bir ad ve tutar girin.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    companion object {
        const val TAG = "TransactionDialog"
        private const val ARG_TRANSACTION_ID = "transaction_id"

        fun newInstance(transactionId: Int?): TransactionDialogFragment {
            val fragment = TransactionDialogFragment()
            if (transactionId != null) {
                fragment.arguments = bundleOf(ARG_TRANSACTION_ID to transactionId)
            }
            return fragment
        }
    }
}