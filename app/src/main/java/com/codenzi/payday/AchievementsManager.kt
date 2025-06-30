package com.codenzi.payday

object AchievementsManager {

    fun getAllAchievements(): List<Achievement> {
        return listOf(
            // --- AŞAMA 1: İLK ADIMLAR (1. Hafta) ---
            Achievement("SETUP_COMPLETE", "Her Şey Hazır!", "Kurulum sihirbazını başarıyla tamamladın.", false, R.drawable.ic_settings),
            Achievement("FIRST_GOAL", "İlk Adım", "İlk tasarruf hedefini oluşturdun.", false, R.drawable.ic_add_goal),
            Achievement("FIRST_TRANSACTION", "Harcama Günlüğü", "İlk harcamanı kaydettin.", false, R.drawable.ic_add_expense),
            Achievement("REPORTS_VIEWED", "Analist", "Raporlar ekranını ilk kez ziyaret ettin.", false, R.drawable.ic_reports),
            Achievement("BACKUP_HERO", "Veri Koruyucu", "Verilerini ilk kez Google Drive'a yedekledin.", false, R.drawable.ic_google_logo),

            // --- AŞAMA 2: ALIŞKANLIK KAZANMA (1. Ay) ---
            Achievement("STREAK_7_DAYS", "Azimli", "Uygulamayı 7 gün boyunca kullandın.", false, R.drawable.ic_achievement_calendar),
            Achievement("SAVER_LV1", "Kumbaracı", "Tasarruf hedeflerin için toplam 1.000 TL biriktirdin.", false, R.drawable.ic_achievement_money),
            Achievement("CATEGORY_EXPERT", "Kategori Uzmanı", "5 farklı harcama kategorisini de kullandın.", false, R.drawable.ic_category_other),
            Achievement("AUTO_PILOT", "Otomatik Pilot", "Tekrarlayan bir harcama şablonu oluşturdun.", false, R.drawable.autorenew),
            Achievement("PAYDAY_HYPE", "Maaş Günü!", "İlk maaş gününü başarıyla tamamladın.", false, R.drawable.ic_achievement_payday),

            // --- AŞAMA 3: FİNANSAL USTALIK (2. Aydan 11. Aya) ---
            Achievement("STREAK_30_DAYS", "Maratoncu", "Uygulamayı 30 gün boyunca kullandın.", false, R.drawable.ic_achievement_calendar),
            Achievement("GOAL_COMPLETED", "Hedef Avcısı", "İlk tasarruf hedefini başarıyla tamamladın.", false, R.drawable.ic_achievement_goal),
            Achievement("BUDGET_WIZARD", "Bütçe Sihirbazı", "Bir maaş döngüsünü, bütçen pozitifteyken bitirdin.", false, R.drawable.ic_reports),
            Achievement("SAVER_LV2", "Yatırımcı", "Tasarruf hedeflerin için toplam 10.000 TL biriktirdin.", false, R.drawable.ic_achievement_money),
            Achievement("COLLECTOR", "Koleksiyoncu", "Aynı anda 3 veya daha fazla aktif tasarruf hedefin var.", false, R.drawable.inventory_2),

            // --- AŞAMA 4: PAYDAY EFSANESİ (1. Yıl ve Ötesi) ---
            Achievement("STREAK_180_DAYS", "Demirbaş", "Uygulamayı 6 ay (180 gün) boyunca kullandın.", false, R.drawable.ic_achievement_calendar),
            Achievement("SAVER_LV3", "Tasarruf Gurusu", "Tasarruf hedeflerin için toplam 50.000 TL biriktirdin.", false, R.drawable.ic_achievement_money),
            Achievement("CYCLE_CHAMPION", "Döngü Şampiyonu", "Arka arkaya 3 maaş döngüsünü pozitif bakiye ile bitirdin.", false, R.drawable.workspace_premium),
            Achievement("DARK_SIDE", "Kara Kutu", "Uygulamanın Koyu Tema'sını keşfettin.", false, R.drawable.nightlight),
            Achievement("LEGEND_ONE_YEAR", "Payday Efsanesi", "1. yılını bizimle tamamladın. Tebrikler!", false, R.drawable.military_tech)
        )
    }
}