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
    private var accumulatedAmountForGoals: Double = 0.0 // GÜNCELLENDİ: İsim daha açıklayıcı hale getirildi.

    // GÜNCELLENDİ: Fonksiyon artık hedefler için özel birikmiş tutarı alıyor.
    fun submitList(newGoals: List<SavingsGoal>, newAccumulatedAmountForGoals: Double) {
        goals = newGoals
        accumulatedAmountForGoals = newAccumulatedAmountForGoals
        notifyDataSetChanged() // Listeyi yenile
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingsGoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_savings_goal, parent, false)
        return SavingsGoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavingsGoalViewHolder, position: Int) {
        val goal = goals[position]
        // GÜNCELLENDİ: ViewHolder'a hedefler için birikmiş tutarı iletiyoruz.
        holder.bind(goal, accumulatedAmountForGoals)
    }

    override fun getItemCount(): Int = goals.size

    class SavingsGoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.goalNameTextView)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.goalProgressBar)
        private val progressTextView: TextView = itemView.findViewById(R.id.goalProgressTextView)
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

        // GÜNCELLENDİ: Parametre adı daha açıklayıcı
        fun bind(goal: SavingsGoal, accumulatedAmountForGoals: Double) {
            nameTextView.text = goal.name

            val progressPercentage = if (goal.targetAmount > 0) {
                // Hesaplama artık hedefler için özel birikime göre yapılıyor.
                (accumulatedAmountForGoals / goal.targetAmount * 100).toInt()
            } else {
                0
            }

            progressBar.progress = progressPercentage.coerceIn(0, 100)

            // Gösterilen birikmiş tutar, hedef tutarını geçemez.
            val accumulatedFormatted = currencyFormatter.format(accumulatedAmountForGoals.coerceAtMost(goal.targetAmount))
            val targetFormatted = currencyFormatter.format(goal.targetAmount)
            progressTextView.text = "$accumulatedFormatted / $targetFormatted"
        }
    }
}
