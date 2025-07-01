package com.codenzi.payday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Date

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
                    if (paydayValue < 1) return null

                    // Bu ayın maaş gününü hesapla
                    val dayInCurrentMonth = minOf(paydayValue, today.month.length(today.isLeapYear))
                    val thisMonthPayday = today.withDayOfMonth(dayInCurrentMonth)

                    if (today.isAfter(thisMonthPayday)) {
                        // Eğer bu ayın maaş günü geçtiyse, bir sonraki ayınkini hesapla
                        val nextMonthDate = today.plusMonths(1)
                        val dayInNextMonth = minOf(paydayValue, nextMonthDate.lengthOfMonth())
                        nextPayday = nextMonthDate.withDayOfMonth(dayInNextMonth)
                        previousPayday = thisMonthPayday
                    } else {
                        // Eğer maaş günü bugün veya bu ayın ilerisindeyse, bu ayınkini kullan
                        nextPayday = thisMonthPayday
                        val prevMonthDate = today.minusMonths(1)
                        val dayInPrevMonth = minOf(paydayValue, prevMonthDate.lengthOfMonth())
                        previousPayday = prevMonthDate.withDayOfMonth(dayInPrevMonth)
                    }
                }
                PayPeriod.WEEKLY -> {
                    if (paydayValue < 1) return null
                    val payDayOfWeek = DayOfWeek.of(paydayValue)
                    // nextOrSame metodu, eğer bugün maaş günüyse bugünü, değilse bir sonraki tarihi verir.
                    nextPayday = today.with(TemporalAdjusters.nextOrSame(payDayOfWeek))
                    previousPayday = nextPayday.minusWeeks(1)
                }
                PayPeriod.BI_WEEKLY -> {
                    val referenceDate = LocalDate.parse(biWeeklyRefDateString) ?: return null
                    var tempPayday = referenceDate
                    // Maaş gününü bugüne veya bugünden sonrasına denk getirene kadar ileri sar
                    while (tempPayday.isBefore(today)) {
                        tempPayday = tempPayday.plusDays(14)
                    }
                    // bulunan tarih bir sonraki maaş günüdür.
                    nextPayday = tempPayday
                    previousPayday = nextPayday.minusDays(14)
                }
            }

            // Döngünün gerçek bitiş tarihini (hafta sonu ayarı olmadan) sakla
            val originalNextPayday = nextPayday

            // Hafta sonu ayarını uygula
            if (weekendAdjustmentEnabled) {
                if (nextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    nextPayday = nextPayday.minusDays(1)
                } else if (nextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    nextPayday = nextPayday.minusDays(2)
                }
            }

            val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)
            val isPayday = daysLeft <= 0L
            // Döngü süresini orijinal (ayarlanmamış) tarihlere göre hesapla
            val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = isPayday,
                totalDaysInCycle = totalDaysInCycle,
                cycleStartDate = previousPayday,
                cycleEndDate = originalNextPayday.minusDays(1)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

fun LocalDate.toDate(): Date {
    return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}
