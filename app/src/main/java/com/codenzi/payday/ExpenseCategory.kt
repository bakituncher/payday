package com.codenzi.payday

import android.content.Context

enum class ExpenseCategory(val stringResId: Int, val iconResId: Int) {
    FOOD(R.string.category_food, R.drawable.ic_category_food),
    TRANSPORT(R.string.category_transport, R.drawable.ic_category_transport),
    BILLS(R.string.category_bills, R.drawable.ic_category_bills),
    SHOPPING(R.string.category_shopping, R.drawable.ic_category_shopping),
    ENTERTAINMENT(R.string.category_entertainment, R.drawable.ic_category_entertainment),
    SAVINGS(R.string.category_savings, R.drawable.ic_achievement_money),
    OTHER(R.string.category_other, R.drawable.ic_category_other);

    fun getDisplayName(context: Context): String {
        return context.getString(stringResId)
    }

    companion object {
        fun fromId(id: Int): ExpenseCategory {
            return entries.getOrNull(id) ?: OTHER
        }

        fun getSavingsCategoryId(): Int {
            return SAVINGS.ordinal
        }
    }
}