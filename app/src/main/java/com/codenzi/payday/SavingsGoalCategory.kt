package com.codenzi.payday

import android.content.Context

enum class SavingsGoalCategory(val stringResId: Int, val iconResId: Int) {
    CAR(R.string.category_goal_car, R.drawable.ic_goal_car),
    HOUSE(R.string.category_goal_house, R.drawable.ic_goal_house),
    TECH(R.string.category_goal_tech, R.drawable.ic_goal_tech),
    TRAVEL(R.string.category_goal_travel, R.drawable.ic_goal_travel),
    OTHER(R.string.category_goal_other, R.drawable.ic_goal_other);

    fun getDisplayName(context: Context): String {
        return context.getString(stringResId)
    }

    companion object {
        fun fromId(id: Int): SavingsGoalCategory {
            return entries.getOrNull(id) ?: OTHER
        }
    }
}