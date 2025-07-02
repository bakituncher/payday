package com.codenzi.payday

import java.util.UUID

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: Long? = null,
    val categoryId: Int = SavingsGoalCategory.OTHER.ordinal,
    // YENİ: Otomatik birikim dağıtımı için kullanıcı tarafından belirlenen oran (yüzde).
    // Varsayılan olarak 100'dür, böylece tek bir hedef varsa tüm birikimi alır.
    val portion: Int = 100
)
