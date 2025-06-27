package com.example.payday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SavingsGoalAdapter(
    private val onEditClicked: (SavingsGoal) -> Unit,
    private val onDeleteClicked: (SavingsGoal) -> Unit
) : ListAdapter<SavingsGoal, SavingsGoalAdapter.SavingsGoalViewHolder>(GoalDiffCallback()) {

    var actualAmountAvailableForGoals: Double = 0.0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingsGoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_savings_goal, parent, false)
        return SavingsGoalViewHolder(view, onEditClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: SavingsGoalViewHolder, position: Int) {
        val goal = getItem(position)
        holder.bind(goal, actualAmountAvailableForGoals)
    }

    class SavingsGoalViewHolder(
        itemView: View,
        private val onEditClicked: (SavingsGoal) -> Unit,
        private val onDeleteClicked: (SavingsGoal) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val nameTextView: TextView = itemView.findViewById(R.id.goalNameTextView)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.goalProgressBar)
        private val progressTextView: TextView = itemView.findViewById(R.id.goalProgressTextView)
        // *** YENİ TextView'ı tanımla ***
        private val dateInfoTextView: TextView = itemView.findViewById(R.id.goalDateInfoTextView)

        private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
        private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
        private lateinit var currentGoal: SavingsGoal

        init {
            itemView.setOnLongClickListener {
                showPopupMenu(it)
                true
            }
        }

        private fun showPopupMenu(view: View) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.goal_options_menu, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit_goal -> {
                            onEditClicked(currentGoal)
                            true
                        }
                        R.id.action_delete_goal -> {
                            onDeleteClicked(currentGoal)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        fun bind(goal: SavingsGoal, actualAmountAvailable: Double) {
            currentGoal = goal
            nameTextView.text = goal.name

            val progressPercentage = if (goal.targetAmount > 0) {
                (actualAmountAvailable / goal.targetAmount * 100).toInt()
            } else { 0 }
            progressBar.progress = progressPercentage.coerceIn(0, 100)

            val amountCoveredBySavings = actualAmountAvailable.coerceAtMost(goal.targetAmount)
            val coveredFormatted = currencyFormatter.format(amountCoveredBySavings)
            val targetFormatted = currencyFormatter.format(goal.targetAmount)
            progressTextView.text = "$coveredFormatted / $targetFormatted"

            // *** YENİ EKLENEN MANTIK ***
            if (goal.targetDate != null) {
                val formattedDate = dateFormatter.format(Date(goal.targetDate))
                val currentDate = System.currentTimeMillis()
                val diffInMillis = goal.targetDate - currentDate
                val daysLeft = TimeUnit.MILLISECONDS.toDays(diffInMillis)

                dateInfoTextView.visibility = View.VISIBLE
                dateInfoTextView.text = itemView.context.getString(
                    R.string.goal_date_info, // Yeni bir string resource eklemek daha iyi olur ama şimdilik böyle
                    formattedDate,
                    daysLeft
                )
            } else {
                dateInfoTextView.visibility = View.GONE
            }
        }
    }
}

class GoalDiffCallback : DiffUtil.ItemCallback<SavingsGoal>() {
    override fun areItemsTheSame(oldItem: SavingsGoal, newItem: SavingsGoal): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SavingsGoal, newItem: SavingsGoal): Boolean {
        return oldItem == newItem
    }
}