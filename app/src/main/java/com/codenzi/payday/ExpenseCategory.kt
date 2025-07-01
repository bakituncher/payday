package com.codenzi.payday

enum class ExpenseCategory(val categoryName: String, val iconResId: Int) {
    FOOD("Yemek", R.drawable.ic_category_food),
    TRANSPORT("Ulaşım", R.drawable.ic_category_transport),
    BILLS("Faturalar", R.drawable.ic_category_bills),
    SHOPPING("Alışveriş", R.drawable.ic_category_shopping),
    ENTERTAINMENT("Eğlence", R.drawable.ic_category_entertainment),
    SAVINGS("Tasarruf", R.drawable.ic_achievement_money),
    OTHER("Diğer", R.drawable.ic_category_other);

    companion object {
        fun fromId(id: Int): ExpenseCategory {
            return entries.getOrNull(id) ?: OTHER
        }

        // YENİ: Tasarruf kategorisinin ID'sini döndüren yardımcı fonksiyon
        fun getSavingsCategoryId(): Int {
            return SAVINGS.ordinal
        }
    }
}
