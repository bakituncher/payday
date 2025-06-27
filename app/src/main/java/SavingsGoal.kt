// Konum: app/src/main/java/SavingsGoal.kt

package com.example.payday

import java.util.UUID

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: Long? = null
)