package com.example.payday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter(
    private val onEditClicked: (Transaction) -> Unit,
    private val onDeleteClicked: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_transaction, parent, false)
        return TransactionViewHolder(view, onEditClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = getItem(position)
        holder.bind(transaction)
    }

    class TransactionViewHolder(
        itemView: View,
        private val onEditClicked: (Transaction) -> Unit,
        private val onDeleteClicked: (Transaction) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.transactionNameTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.transactionDateTextView)
        private val amountTextView: TextView = itemView.findViewById(R.id.transactionAmountTextView)
        private val iconImageView: ImageView = itemView.findViewById(R.id.transactionIcon) // DÜZELTME: İkon için referans eklendi.
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
        private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
        private lateinit var currentTransaction: Transaction

        init {
            itemView.setOnLongClickListener {
                showPopupMenu(it)
                true
            }
        }

        private fun showPopupMenu(view: View) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.transaction_options_menu, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit_transaction -> {
                            onEditClicked(currentTransaction)
                            true
                        }
                        R.id.action_delete_transaction -> {
                            onDeleteClicked(currentTransaction)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        fun bind(transaction: Transaction) {
            currentTransaction = transaction
            nameTextView.text = transaction.name
            dateTextView.text = dateFormatter.format(transaction.date)
            amountTextView.text = "- ${currencyFormatter.format(transaction.amount)}"

            // DÜZELTME: Kategoriye göre doğru ikonu ayarlıyoruz.
            val category = ExpenseCategory.fromId(transaction.categoryId)
            iconImageView.setImageResource(category.iconResId)
        }
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
    override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem == newItem
    }
}
