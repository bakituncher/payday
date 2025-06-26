package com.example.payday

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val amount: Double,
    val date: Date,
    val categoryId: Int // DÜZELTME: Kategori ID'si eklendi.
)