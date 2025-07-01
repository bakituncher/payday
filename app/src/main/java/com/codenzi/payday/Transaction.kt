package com.codenzi.payday

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val amount: Double,
    // --- DÜZELTME: 'var' -> 'val' olarak değiştirilerek veri bütünlüğü korundu ---
    val date: Date,
    val categoryId: Int,
    val isRecurringTemplate: Boolean = false
)