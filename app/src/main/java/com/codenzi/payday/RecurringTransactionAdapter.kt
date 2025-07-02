package com.codenzi.payday

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.codenzi.payday.databinding.ListItemRecurringTransactionBinding
import java.text.NumberFormat
import java.util.Locale

class RecurringTransactionAdapter(
    private val onEditClicked: (Transaction) -> Unit,
    private val onDeleteClicked: (Transaction) -> Unit
) : ListAdapter<Transaction, RecurringTransactionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemRecurringTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onEditClicked, onDeleteClicked)
    }

    class ViewHolder(private val binding: ListItemRecurringTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

        fun bind(
            transaction: Transaction,
            onEditClicked: (Transaction) -> Unit,
            onDeleteClicked: (Transaction) -> Unit
        ) {
            binding.transactionNameTextView.text = transaction.name
            binding.transactionAmountTextView.text = "- ${currencyFormatter.format(transaction.amount)}"
            val category = ExpenseCategory.fromId(transaction.categoryId)
            binding.transactionCategoryTextView.text = category.categoryName
            binding.transactionIcon.setImageResource(category.iconResId)

            binding.editButton.setOnClickListener { onEditClicked(transaction) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(transaction) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}