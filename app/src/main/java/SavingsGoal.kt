package com.codenzi.payday

import java.util.UUID

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: Long? = null,
    val categoryId: Int = SavingsGoalCategory.OTHER.ordinal,
    val portion: Int = 100
) {
    val isComplete: Boolean
        get() = savedAmount >= targetAmount
}