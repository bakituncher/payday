package com.example.payday

import java.util.UUID

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    // *** YENİ ALAN BURADA EKLENDİ ***
    // Null olabilir, çünkü kullanıcı tarih belirlemek zorunda değil.
    val targetDate: Long? = null
)