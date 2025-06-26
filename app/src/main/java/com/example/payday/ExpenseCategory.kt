package com.example.payday

// Harcama kategorilerini ve ilgili ikonlarını tanımlayan enum sınıfı.
enum class ExpenseCategory(val categoryName: String, val iconResId: Int) {
    FOOD("Yemek", R.drawable.ic_category_food),
    TRANSPORT("Ulaşım", R.drawable.ic_category_transport),
    BILLS("Faturalar", R.drawable.ic_category_bills),
    SHOPPING("Alışveriş", R.drawable.ic_category_shopping),
    ENTERTAINMENT("Eğlence", R.drawable.ic_category_entertainment),
    OTHER("Diğer", R.drawable.ic_category_other);

    companion object {
        // ID'ye göre kategoriyi bulmak için yardımcı fonksiyon.
        fun fromId(id: Int): ExpenseCategory {
            return values().getOrNull(id) ?: OTHER
        }
    }
}