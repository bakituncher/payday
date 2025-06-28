package com.codenzi.payday

// Veritabanı sorgusunun sonucunu tutmak için kullanılacak veri sınıfı.
data class CategorySpending(
    val categoryId: Int,
    val totalAmount: Double
)