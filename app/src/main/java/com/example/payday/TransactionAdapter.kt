package com.example.payday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
    private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = getItem(position)
        holder.bind(transaction, currencyFormatter, dateFormatter)
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.transactionNameTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.transactionDateTextView)
        private val amountTextView: TextView = itemView.findViewById(R.id.transactionAmountTextView)

        fun bind(transaction: Transaction, currencyFormatter: NumberFormat, dateFormatter: SimpleDateFormat) {
            nameTextView.text = transaction.name
            dateTextView.text = dateFormatter.format(transaction.date)
            amountTextView.text = "- ${currencyFormatter.format(transaction.amount)}"
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