// Dosya: app/src/main/java/com/example/payday/AchievementsManager.kt

package com.example.payday

object AchievementsManager {

    // UYGULAMA İÇİNDEKİ TÜM BAŞARIMLARI BURADA TANIMLIYORUZ
    fun getAllAchievements(): List<Achievement> {
        return listOf(
            Achievement(
                id = "FIRST_GOAL",
                title = "İlk Adım",
                description = "İlk tasarruf hedefini oluşturdun!",
                iconResId = R.drawable.ic_achievement_goal
            ),
            Achievement(
                id = "FIRST_WEEK",
                title = "Azimli",
                description = "Uygulamayı 7 gün boyunca kullandın!",
                iconResId = R.drawable.ic_achievement_calendar
            ),
            Achievement(
                id = "PAYDAY_HYPE",
                title = "Maaş Günü!",
                description = "İlk maaş gününü başarıyla tamamladın.",
                iconResId = R.drawable.ic_achievement_payday
            ),
            Achievement(
                id = "SAVER_LV1",
                title = "Kumbaracı",
                description = "Tasarruf hedeflerin için 1000 TL biriktirdin.",
                iconResId = R.drawable.ic_achievement_money
            )
            // Gelecekte buraya yeni başarımlar ekleyebiliriz.
        )
    }
}