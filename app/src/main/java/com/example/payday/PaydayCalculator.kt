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
    val accumulatedAmount: Double,
    val totalDaysInCycle: Long // Bu parametre MainActivity'de kullanılacak
)

// Central object for all calculation logic
object PaydayCalculator {

    fun calculate(context: Context): PaydayResult? {
        val prefs = context.getSharedPreferences("PaydayPrefs", Context.MODE_PRIVATE)

        // 1. Tüm ayarları SharedPreferences'tan oku
        val paydayOfMonth = prefs.getInt("PaydayOfMonth", -1)
        val weekendAdjustmentEnabled = prefs.getBoolean("WeekendAdjustmentEnabled", false)
        val salaryAmount = prefs.getLong("SalaryAmount", 0L)
        val payPeriod = PayPeriod.valueOf(prefs.getString("PayPeriod", PayPeriod.MONTHLY.name)!!)
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
                    val payDayOfWeek = DayOfWeek.of(paydayOfMonth)
                    nextPayday = today.with(TemporalAdjusters.nextOrSame(payDayOfWeek))
                    previousPayday = if(nextPayday == today) {
                        today
                    } else {
                        today.with(TemporalAdjusters.previous(payDayOfWeek))
                    }
                }

                PayPeriod.BI_WEEKLY -> {
                    val referenceDate = LocalDate.parse(biWeeklyRefDateString)
                    var tempPayday = referenceDate
                    while (tempPayday.isBefore(today)) {
                        tempPayday = tempPayday.plusDays(14)
                    }
                    nextPayday = tempPayday
                    previousPayday = nextPayday.minusDays(14)
                }
            }

            // --- Bundan sonraki mantık tüm periyotlar için ortaktır ---

            // 3. Hafta sonu ayarlamasını yap
            val originalNextPayday = nextPayday
            if (weekendAdjustmentEnabled) {
                if (nextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    nextPayday = nextPayday.minusDays(1)
                } else if (nextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    nextPayday = nextPayday.minusDays(2)
                }
            }

            // 4. Kalan gün sayısını hesapla
            val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)

            // 5. Birikmiş tutarı ve döngüdeki toplam günü hesapla
            var accumulatedAmount = 0.0
            val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)

            if (salaryAmount > 0) {
                val daysPassed = ChronoUnit.DAYS.between(previousPayday, today)
                if (totalDaysInCycle > 0) {
                    val cycleSalary = when (payPeriod) {
                        PayPeriod.WEEKLY -> salaryAmount / 4.0
                        PayPeriod.BI_WEEKLY -> salaryAmount / 2.0
                        PayPeriod.MONTHLY -> salaryAmount.toDouble()
                    }
                    val dailyRate = cycleSalary / totalDaysInCycle
                    accumulatedAmount = dailyRate * daysPassed
                }
            }

            // 6. Sonucu döndür (DÜZELTİLMİŞ KISIM)
            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L,
                accumulatedAmount = accumulatedAmount,
                totalDaysInCycle = totalDaysInCycle // Hatanın olduğu eksik parametre eklendi.
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}