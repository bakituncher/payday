package com.codenzi.payday

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

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
            title.text = itemView.context.getString(achievement.titleResId)
            description.text = itemView.context.getString(achievement.descriptionResId)
            icon.setImageResource(achievement.iconResId)

            val iconBackground = icon.background.mutate() as GradientDrawable

            if (achievement.isUnlocked) {
                // Kilidi açık başarım
                container.alpha = 1.0f
                val unlockedColors = intArrayOf(
                    ContextCompat.getColor(itemView.context, R.color.primary),
                    ContextCompat.getColor(itemView.context, R.color.secondary)
                )
                iconBackground.colors = unlockedColors
            } else {
                // Kilitli başarım
                container.alpha = 0.5f
                val lockedColor = ContextCompat.getColor(itemView.context, R.color.text_tertiary)
                // DÜZELTİLMİŞ KISIM: Arka planı tek ve düz bir renkten oluşan bir gradyan olarak ayarla
                iconBackground.colors = intArrayOf(lockedColor, lockedColor)
            }
        }
    }
}