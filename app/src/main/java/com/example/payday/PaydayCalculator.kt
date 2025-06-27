// app/src/main/java/com/example/payday/PaydayCalculator.kt

package com.example.payday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Date

// GÜNCELLENDİ: Maaş döngüsünün başlangıç ve bitiş tarihlerini ekliyoruz.
data class PaydayResult(
    val daysLeft: Long,
    val isPayday: Boolean,
    val totalDaysInCycle: Long,
    val cycleStartDate: LocalDate,
    val cycleEndDate: LocalDate
)

object PaydayCalculator {

    fun calculate(
        payPeriod: PayPeriod,
        paydayValue: Int,
        biWeeklyRefDateString: String?,
        weekendAdjustmentEnabled: Boolean
    ): PaydayResult? {
        try {
            val today = LocalDate.now()
            var nextPayday: LocalDate
            var previousPayday: LocalDate

            when (payPeriod) {
                PayPeriod.MONTHLY -> {
                    if (paydayValue < 1) return null // Geçersiz gün
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
                    if (paydayValue < 1) return null // Geçersiz gün
                    val payDayOfWeek = DayOfWeek.of(paydayValue)
                    nextPayday = today.with(TemporalAdjusters.next(payDayOfWeek))
                    if (today.dayOfWeek == payDayOfWeek) {
                        nextPayday = today.plusWeeks(1)
                    }
                    previousPayday = nextPayday.minusWeeks(1)
                }
                PayPeriod.BI_WEEKLY -> {
                    val referenceDate = LocalDate.parse(biWeeklyRefDateString) ?: return null
                    var tempPayday = referenceDate
                    while (tempPayday.isBefore(today)) {
                        tempPayday = tempPayday.plusDays(14)
                    }
                    if (tempPayday == today) {
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

            // Maaş günleri arasındaki gerçek gün sayısı
            val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L,
                totalDaysInCycle = totalDaysInCycle,
                cycleStartDate = previousPayday,
                cycleEndDate = originalNextPayday.minusDays(1) // Döngü bir sonraki maaş gününden 1 gün önce biter
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
// LocalDate'i Date'e çevirmek için yardımcı fonksiyon
fun LocalDate.toDate(): Date {
    return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}