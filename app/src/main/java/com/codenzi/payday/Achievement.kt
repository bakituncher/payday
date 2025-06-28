// Dosya: app/src/main/java/com/example/payday/Achievement.kt

package com.codenzi.payday

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    var isUnlocked: Boolean = false,
    val iconResId: Int // Başarım için özel ikon
)