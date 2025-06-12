package com.example.payday

import java.time.DayOfWeek
import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Hesaplama sonucunu temiz bir şekilde tutmak için bir veri sınıfı (data class)
data class PaydayResult(
    val daysLeft: Long,
    val isPayday: Boolean
)

// Hesaplama mantığını barındıran merkezi object (singleton)
object PaydayCalculator {

    // PaydayCalculator.kt içindeki calculate fonksiyonunun YENİ hali
    fun calculate(context: Context): PaydayResult? {
        val prefs = context.getSharedPreferences("PaydayPrefs", Context.MODE_PRIVATE)
        val paydayOfMonth = prefs.getInt("PaydayOfMonth", -1)
        // Kullanıcının hafta sonu ayarını tercihini alıyoruz
        val weekendAdjustmentEnabled = prefs.getBoolean("WeekendAdjustmentEnabled", false)

        if (paydayOfMonth == -1) {
            return null
        }

        try {
            val today = LocalDate.now()
            var paydayInCurrentMonth = today.withDayOfMonth(paydayOfMonth)

            var nextPayday = if (today.isAfter(paydayInCurrentMonth)) {
                val nextMonth = today.plusMonths(1)
                val lastDayOfNextMonth = nextMonth.lengthOfMonth()
                nextMonth.withDayOfMonth(minOf(paydayOfMonth, lastDayOfNextMonth))
            } else {
                paydayInCurrentMonth
            }

            // --- YENİ HAFTA SONU KONTROL MANTIĞI ---
            if (weekendAdjustmentEnabled) {
                if (nextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    nextPayday = nextPayday.minusDays(1) // Cumartesi ise Cuma'ya çek
                } else if (nextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    nextPayday = nextPayday.minusDays(2) // Pazar ise Cuma'ya çek
                }
            }
            // --- KONTROL BİTTİ ---

            val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L // Maaş günü veya Cuma'dan dolayı daha erken ise
            )
        } catch (e: Exception) {
            return null
        }
    }
}