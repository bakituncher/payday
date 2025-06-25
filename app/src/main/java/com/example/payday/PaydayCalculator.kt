package com.example.payday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// GÜNCELLENDİ: Grafik verisi için accumulationData eklendi.
data class PaydayResult(
    val daysLeft: Long,
    val isPayday: Boolean,
    val accumulatedAmount: Double,
    val totalDaysInCycle: Long,
    val accumulationData: List<Pair<Int, Double>> // <Döngüdeki Gün, Biriken Miktar>
)

object PaydayCalculator {

    fun calculate(
        payPeriod: PayPeriod,
        paydayValue: Int, // Hem ayın günü hem de haftanın günü için kullanılır
        biWeeklyRefDateString: String?,
        salaryAmount: Long,
        weekendAdjustmentEnabled: Boolean
    ): PaydayResult? {
        // ... (fonksiyonun başlangıcı aynı)
        try {
            val today = LocalDate.now()
            var nextPayday: LocalDate
            var previousPayday: LocalDate

            // ... (maaş günü hesaplama mantığı aynı)

            when (payPeriod) {
                PayPeriod.MONTHLY -> {
                    val dayToUse = minOf(paydayValue, today.month.length(today.isLeapYear))
                    nextPayday = if (today.dayOfMonth >= dayToUse) {
                        val nextMonth = today.plusMonths(1)
                        val lastDayOfNextMonth = nextMonth.lengthOfMonth()
                        nextMonth.withDayOfMonth(minOf(paydayValue, lastDayOfNextMonth))
                    } else {
                        today.withDayOfMonth(dayToUse)
                    }
                    previousPayday = if (today.dayOfMonth >= dayToUse) {
                        today.withDayOfMonth(dayToUse)
                    } else {
                        val prevMonth = today.minusMonths(1)
                        val lastDayOfPrevMonth = prevMonth.lengthOfMonth()
                        prevMonth.withDayOfMonth(minOf(paydayValue, lastDayOfPrevMonth))
                    }
                }
                PayPeriod.WEEKLY -> {
                    val payDayOfWeek = DayOfWeek.of(paydayValue)
                    nextPayday = today.with(TemporalAdjusters.nextOrSame(payDayOfWeek))
                    previousPayday = if(nextPayday == today) today else today.with(TemporalAdjusters.previous(payDayOfWeek))
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


            val originalNextPayday = nextPayday
            if (weekendAdjustmentEnabled) {
                if (nextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    nextPayday = nextPayday.minusDays(1)
                } else if (nextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    nextPayday = nextPayday.minusDays(2)
                }
            }

            val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)
            val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)

            // GÜNCELLENDİ: Hem anlık birikimi hem de grafik verisini hesapla
            var accumulatedAmount = 0.0
            val accumulationData = mutableListOf<Pair<Int, Double>>()
            if (salaryAmount > 0 && totalDaysInCycle > 0) {
                val cycleSalary = when (payPeriod) {
                    PayPeriod.WEEKLY -> salaryAmount / 4.0
                    PayPeriod.BI_WEEKLY -> salaryAmount / 2.0
                    PayPeriod.MONTHLY -> salaryAmount.toDouble()
                }
                val dailyRate = cycleSalary / totalDaysInCycle

                // Grafik verisini oluştur
                for (i in 0..totalDaysInCycle) {
                    val dateInCycle = previousPayday.plusDays(i)
                    if (dateInCycle.isAfter(today)) break // Geleceği çizme
                    val amountForDay = dailyRate * i
                    accumulationData.add(Pair(i.toInt(), amountForDay))
                }

                // Anlık birikimi hesapla
                val daysPassed = ChronoUnit.DAYS.between(previousPayday, today)
                accumulatedAmount = dailyRate * daysPassed.coerceAtLeast(0)
            }


            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L,
                accumulatedAmount = accumulatedAmount,
                totalDaysInCycle = totalDaysInCycle,
                accumulationData = accumulationData // Yeni veriyi ekle
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
