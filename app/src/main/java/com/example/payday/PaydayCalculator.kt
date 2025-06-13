// Dosya: app/src/main/java/com/example/payday/PaydayCalculator.kt

package com.example.payday

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Calculation result data class
data class PaydayResult(
    val daysLeft: Long,
    val isPayday: Boolean,
    val accumulatedAmount: Double // Field for the accumulated amount
)

// Central object for all calculation logic
object PaydayCalculator {

    fun calculate(context: Context): PaydayResult? {
        val prefs = context.getSharedPreferences("PaydayPrefs", Context.MODE_PRIVATE)

        // 1. Tüm ayarları SharedPreferences'tan oku
        val paydayOfMonth = prefs.getInt("PaydayOfMonth", -1)
        val weekendAdjustmentEnabled = prefs.getBoolean("WeekendAdjustmentEnabled", false)
        val salaryAmount = prefs.getLong("SalaryAmount", 0L)
        // Yeni eklediğimiz periyot ayarını oku, varsayılan olarak Aylık (MONTHLY)
        val payPeriod = PayPeriod.valueOf(prefs.getString("PayPeriod", PayPeriod.MONTHLY.name)!!)
        // 2 Haftada bir ödeme için referans tarihini oku (sonraki adımda kaydedeceğiz)
        val biWeeklyRefDateString = prefs.getString("BiWeeklyRefDate", null)


        if (paydayOfMonth == -1 && payPeriod != PayPeriod.BI_WEEKLY) {
            return null // Gün ayarlanmamışsa null dön
        }
        if (payPeriod == PayPeriod.BI_WEEKLY && biWeeklyRefDateString == null) {
            return null // 2 haftada bir için referans tarihi ayarlanmamışsa null dön
        }


        try {
            val today = LocalDate.now()
            var nextPayday: LocalDate
            val previousPayday: LocalDate

            // 2. Seçilen ödeme periyoduna göre BİR SONRAKİ ve BİR ÖNCEKİ maaş gününü hesapla
            when (payPeriod) {
                PayPeriod.MONTHLY -> {
                    // Bu blok, sizin orijinal kodunuzun aynısıdır.
                    nextPayday = if (today.dayOfMonth >= paydayOfMonth) {
                        val nextMonth = today.plusMonths(1)
                        val lastDayOfNextMonth = nextMonth.lengthOfMonth()
                        nextMonth.withDayOfMonth(minOf(paydayOfMonth, lastDayOfNextMonth))
                    } else {
                        today.withDayOfMonth(paydayOfMonth)
                    }

                    previousPayday = if (today.dayOfMonth >= paydayOfMonth) {
                        today.withDayOfMonth(paydayOfMonth)
                    } else {
                        today.minusMonths(1).withDayOfMonth(paydayOfMonth)
                    }
                }

                PayPeriod.WEEKLY -> {
                    // paydayOfMonth burada haftanın gününü (1=Pzt, 7=Pzr) temsil ediyor.
                    val payDayOfWeek = DayOfWeek.of(paydayOfMonth)

                    // Bir sonraki veya bugünkü maaş gününü bul
                    nextPayday = today.with(TemporalAdjusters.nextOrSame(payDayOfWeek))

                    // Eğer bugün maaş günü ise, bir önceki gün bugündür. Değilse, geçen haftaki gündür.
                    previousPayday = if(nextPayday == today) {
                        today
                    } else {
                        today.with(TemporalAdjusters.previous(payDayOfWeek))
                    }
                }

                PayPeriod.BI_WEEKLY -> {
                    val referenceDate = LocalDate.parse(biWeeklyRefDateString)
                    var tempPayday = referenceDate
                    // Referans tarihinden başlayarak bugünü geçene kadar 14 gün ekle
                    while (tempPayday.isBefore(today)) {
                        tempPayday = tempPayday.plusDays(14)
                    }
                    nextPayday = tempPayday
                    previousPayday = nextPayday.minusDays(14)
                }
            }

            // --- Bundan sonraki mantık tüm periyotlar için ortaktır ---

            // 3. Hafta sonu ayarlamasını yap
            val originalNextPayday = nextPayday // Orijinal tarihi birikim hesabı için sakla
            if (weekendAdjustmentEnabled) {
                if (nextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    nextPayday = nextPayday.minusDays(1) // Cumartesi ise Cuma yap
                } else if (nextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    nextPayday = nextPayday.minusDays(2) // Pazar ise Cuma yap
                }
            }

            // 4. Kalan gün sayısını hesapla
            val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)

            // 5. Birikmiş tutarı hesapla
            var accumulatedAmount = 0.0
            if (salaryAmount > 0) {
                // Ödeme döngüsündeki toplam gün sayısı
                val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)
                // Son maaş gününden bu yana geçen gün sayısı
                val daysPassed = ChronoUnit.DAYS.between(previousPayday, today)

                if (totalDaysInCycle > 0) {
                    // Maaşı periyoda göre ayarla (Haftalık ise 4'e böl, 2 haftada bir ise 2'ye böl)
                    val cycleSalary = when (payPeriod) {
                        PayPeriod.WEEKLY -> salaryAmount / 4.0
                        PayPeriod.BI_WEEKLY -> salaryAmount / 2.0
                        PayPeriod.MONTHLY -> salaryAmount.toDouble()
                    }
                    val dailyRate = cycleSalary / totalDaysInCycle
                    accumulatedAmount = dailyRate * daysPassed
                }
            }

            // 6. Sonucu döndür
            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L,
                accumulatedAmount = accumulatedAmount
            )
        } catch (e: Exception) {
            // Hata durumunda (örn: geçersiz tarih) null dön
            e.printStackTrace()
            return null
        }
    }
}