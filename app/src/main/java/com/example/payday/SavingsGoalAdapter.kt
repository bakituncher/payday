// Dosya: app/src/main/java/com/example/payday/SavingsGoalAdapter.kt

package com.example.payday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

// YENİ: Adapter'ın constructor'ına iki adet lambda fonksiyonu ekliyoruz
class SavingsGoalAdapter(
    private val onDeleteClicked: (SavingsGoal) -> Unit
) : RecyclerView.Adapter<SavingsGoalAdapter.SavingsGoalViewHolder>() {

    private var goals: List<SavingsGoal> = emptyList()
    private var accumulatedAmountForGoals: Double = 0.0

    fun submitList(newGoals: List<SavingsGoal>, newAccumulatedAmountForGoals: Double) {
        goals = newGoals
        accumulatedAmountForGoals = newAccumulatedAmountForGoals
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingsGoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_savings_goal, parent, false)
        // YENİ: ViewHolder oluşturulurken lambda'ları iletiyoruz
        return SavingsGoalViewHolder(view, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: SavingsGoalViewHolder, position: Int) {
        val goal = goals[position]
        holder.bind(goal, accumulatedAmountForGoals)
    }

    override fun getItemCount(): Int = goals.size

    // YENİ: ViewHolder da constructor'da lambda alacak
    class SavingsGoalViewHolder(
        itemView: View,
        private val onDeleteClicked: (SavingsGoal) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        // ... (diğer değişkenler aynı)
        private val nameTextView: TextView = itemView.findViewById(R.id.goalNameTextView)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.goalProgressBar)
        private val progressTextView: TextView = itemView.findViewById(R.id.goalProgressTextView)
        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
        private lateinit var currentGoal: SavingsGoal

        init {
            itemView.setOnLongClickListener {
                showPopupMenu(it)
                true // Olayın tüketildiğini belirtir
            }
        }

        private fun showPopupMenu(view: View) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.goal_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_delete_goal -> {
                        onDeleteClicked(currentGoal)
                        true
                    }
                    // TODO: Düzenleme için de benzer bir yapı kurulabilir
                    // R.id.action_edit_goal -> { ... }
                    else -> false
                }
            }
            popup.show()
        }

        fun bind(goal: SavingsGoal, accumulatedAmountForGoals: Double) {
            currentGoal = goal // Menüde kullanmak için hedefi sakla
            // ... (bind metodunun geri kalanı aynı)
            nameTextView.text = goal.name
            val progressPercentage = if (goal.targetAmount > 0) {
                (accumulatedAmountForGoals / goal.targetAmount * 100).toInt()
            } else { 0 }
            progressBar.progress = progressPercentage.coerceIn(0, 100)
            val accumulatedFormatted = currencyFormatter.format(accumulatedAmountForGoals.coerceAtMost(goal.targetAmount))
            val targetFormatted = currencyFormatter.format(goal.targetAmount)
            progressTextView.text = "$accumulatedFormatted / $targetFormatted"
        }
    }
}