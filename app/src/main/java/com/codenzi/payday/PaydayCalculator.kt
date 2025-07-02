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
                    val dayInCurrentMonth = minOf(paydayValue, today.month.length(today.isLeapYear))
                    var thisMonthPayday = today.withDayOfMonth(dayInCurrentMonth)

                    // DÜZELTME: Maaş günü hatasını gidermek için mantık değişikliği.
                    // Eğer bugün maaş günü veya sonrasıysa, bir sonraki maaş günü gelecek ayınkidir.
                    if (today.isAfter(thisMonthPayday) || today.isEqual(thisMonthPayday)) {
                        val nextMonthDate = today.plusMonths(1)
                        val dayInNextMonth = minOf(paydayValue, nextMonthDate.lengthOfMonth())
                        nextPayday = nextMonthDate.withDayOfMonth(dayInNextMonth)
                        previousPayday = thisMonthPayday
                    } else {
                        nextPayday = thisMonthPayday
                        val prevMonthDate = today.minusMonths(1)
                        val dayInPrevMonth = minOf(paydayValue, prevMonthDate.lengthOfMonth())
                        previousPayday = prevMonthDate.withDayOfMonth(dayInPrevMonth)
                    }
                }
                PayPeriod.WEEKLY -> {
                    if (paydayValue < 1) return null
                    val payDayOfWeek = DayOfWeek.of(paydayValue)
                    // DÜZELTME: nextOrSame, eğer bugün maaş günüyse bugünü verir, bu da döngüyü bozar.
                    // Bu yüzden 'next' kullanarak her zaman bir sonraki günü hedefliyoruz.
                    nextPayday = today.with(TemporalAdjusters.next(payDayOfWeek))
                    // Eğer bugün maaş günüyse, bir sonraki maaş günü 7 gün sonradır.
                    if(today.dayOfWeek == payDayOfWeek) {
                        nextPayday = today.plusWeeks(1)
                    }
                    previousPayday = nextPayday.minusWeeks(1)
                }
                PayPeriod.BI_WEEKLY -> {
                    val referenceDate = LocalDate.parse(biWeeklyRefDateString) ?: return null
                    var tempPayday = referenceDate
                    while (tempPayday.isBefore(today) || tempPayday.isEqual(today)) {
                        tempPayday = tempPayday.plusDays(14)
                    }
                    nextPayday = tempPayday
                    previousPayday = nextPayday.minusDays(14)
                }
            }

            val originalNextPayday = nextPayday
            var adjustedNextPayday = nextPayday

            if (weekendAdjustmentEnabled) {
                if (adjustedNextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    adjustedNextPayday = adjustedNextPayday.minusDays(1)
                } else if (adjustedNextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    adjustedNextPayday = adjustedNextPayday.minusDays(2)
                }
            }

            val daysLeft = ChronoUnit.DAYS.between(today, adjustedNextPayday)
            val isPayday = today.isEqual(adjustedNextPayday)

            // DÜZELTME: Döngü başlangıç ve bitiş tarihlerini netleştirme.
            // Mevcut döngü, bir önceki maaş gününde başlar ve bir sonraki maaş gününden bir gün önce biter.
            val cycleStartDate = if (today.isBefore(previousPayday)) {
                // Bu durum normalde olmamalı ama güvenlik için eklendi.
                previousPayday.minusDays(ChronoUnit.DAYS.between(previousPayday, originalNextPayday))
            } else {
                previousPayday
            }

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = isPayday,
                totalDaysInCycle = ChronoUnit.DAYS.between(cycleStartDate, originalNextPayday),
                cycleStartDate = cycleStartDate,
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
