// Dosya: app/src/main/java/com/example/payday/SavingsGoalAdapter.kt

package com.example.payday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class SavingsGoalAdapter : RecyclerView.Adapter<SavingsGoalAdapter.SavingsGoalViewHolder>() {

    private var goals: List<SavingsGoal> = emptyList()
    private var accumulatedAmount: Double = 0.0

    // Veri listesini ve birikmiş tutarı güncelleyip RecyclerView'ı yeniler
    fun submitList(newGoals: List<SavingsGoal>, newAccumulatedAmount: Double) {
        goals = newGoals
        accumulatedAmount = newAccumulatedAmount
        notifyDataSetChanged()
    }

    // Sadece birikmiş tutar değiştiğinde kullanılır (daha verimli)
    fun updateAccumulatedAmount(newAccumulatedAmount: Double) {
        accumulatedAmount = newAccumulatedAmount
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingsGoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_savings_goal, parent, false)
        return SavingsGoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavingsGoalViewHolder, position: Int) {
        val goal = goals[position]
        holder.bind(goal, accumulatedAmount)
    }

    override fun getItemCount(): Int = goals.size

    // Tek bir satırın (ViewHolder) görünümlerini tutan ve veriyi bağlayan sınıf
    class SavingsGoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.goalNameTextView)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.goalProgressBar)
        private val progressTextView: TextView = itemView.findViewById(R.id.goalProgressTextView)
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

        fun bind(goal: SavingsGoal, accumulatedAmount: Double) {
            nameTextView.text = goal.name

            val progressPercentage = if (goal.targetAmount > 0) {
                (accumulatedAmount / goal.targetAmount * 100).toInt()
            } else {
                0
            }

            progressBar.progress = progressPercentage.coerceIn(0, 100)

            val accumulatedFormatted = currencyFormatter.format(accumulatedAmount.coerceAtMost(goal.targetAmount))
            val targetFormatted = currencyFormatter.format(goal.targetAmount)
            progressTextView.text = "$accumulatedFormatted / $targetFormatted"
        }
    }
}