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
    val date: Date,
    val categoryId: Int,
    // *** YENİ ALAN BURADA EKLENDİ ***
    // Bu harcamanın her ayın başında eklenecek bir şablon olup olmadığını belirtir.
    val isRecurringTemplate: Boolean = false
)