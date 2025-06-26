// Dosya: app/src/main/java/com/example/payday/AchievementsAdapter.kt

package com.example.payday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AchievementsAdapter(private val achievements: List<Achievement>) : RecyclerView.Adapter<AchievementsAdapter.AchievementViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(achievements[position])
    }

    override fun getItemCount(): Int = achievements.size

    class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.container)
        private val icon: ImageView = itemView.findViewById(R.id.achievementIcon)
        private val title: TextView = itemView.findViewById(R.id.achievementTitle)
        private val description: TextView = itemView.findViewById(R.id.achievementDescription)

        fun bind(achievement: Achievement) {
            title.text = achievement.title
            description.text = achievement.description
            icon.setImageResource(achievement.iconResId)

            if (achievement.isUnlocked) {
                // Kilidi açık başarım
                container.alpha = 1.0f
                (icon.background as android.graphics.drawable.GradientDrawable).apply {
                    val colors = intArrayOf(
                        itemView.context.getColor(R.color.primary),
                        itemView.context.getColor(R.color.secondary)
                    )
                    setColors(colors)
                }
            } else {
                // Kilitli başarım
                container.alpha = 0.5f
                (icon.background as android.graphics.drawable.GradientDrawable).apply {
                    val colors = intArrayOf(
                        itemView.context.getColor(R.color.text_tertiary),
                        itemView.context.getColor(R.color.text_tertiary)
                    )
                    setColors(colors)
                }
            }
        }
    }
}