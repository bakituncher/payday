// Dosya: app/src/main/java/com/example/payday/SavingsGoal.kt

package com.example.payday

import java.util.UUID

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(), // Her hedefe benzersiz bir kimlik
    val name: String, // Hedefin adı (Örn: "Yeni Telefon")
    val targetAmount: Double // Hedeflenen tutar (Örn: 35000.0)
)