package com.codenzi.payday

// Tasarruf hedeflerini ve ikonlarını yöneten enum sınıfı.
enum class SavingsGoalCategory(val categoryName: String, val iconResId: Int) {
    CAR("Araba", R.drawable.ic_goal_car),
    HOUSE("Ev", R.drawable.ic_goal_house),
    TECH("Teknoloji", R.drawable.ic_goal_tech),
    TRAVEL("Tatil", R.drawable.ic_goal_travel),
    OTHER("Diğer", R.drawable.ic_goal_other);

    companion object {
        fun fromId(id: Int): SavingsGoalCategory {
            return entries.getOrNull(id) ?: OTHER
        }
    }
}