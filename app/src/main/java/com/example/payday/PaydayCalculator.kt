package com.example.payday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class PaydayResult(
    val daysLeft: Long,
    val isPayday: Boolean,
    val accumulatedAmount: Double,
    val totalDaysInCycle: Long
)

object PaydayCalculator {

    fun calculate(
        payPeriod: PayPeriod,
        paydayValue: Int, // Hem ayın günü hem de haftanın günü için kullanılır
        biWeeklyRefDateString: String?,
        salaryAmount: Long,
        weekendAdjustmentEnabled: Boolean
    ): PaydayResult? {
        if (paydayValue == -1 && payPeriod != PayPeriod.BI_WEEKLY) return null
        if (payPeriod == PayPeriod.BI_WEEKLY && biWeeklyRefDateString == null) return null

        try {
            val today = LocalDate.now()
            var nextPayday: LocalDate
            var previousPayday: LocalDate

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
                    // Eğer bugün maaş günü ise, bir önceki maaş günü de bugündür.
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
            var accumulatedAmount = 0.0
            val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)

            if (salaryAmount > 0 && totalDaysInCycle > 0) {
                val daysPassed = ChronoUnit.DAYS.between(previousPayday, today)
                val cycleSalary = when (payPeriod) {
                    PayPeriod.WEEKLY -> salaryAmount / 4.0
                    PayPeriod.BI_WEEKLY -> salaryAmount / 2.0
                    PayPeriod.MONTHLY -> salaryAmount.toDouble()
                }
                val dailyRate = cycleSalary / totalDaysInCycle
                accumulatedAmount = dailyRate * daysPassed.coerceAtLeast(0)
            }

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L,
                accumulatedAmount = accumulatedAmount,
                totalDaysInCycle = totalDaysInCycle
            )
        } catch (e: Exception) {
            // Hata durumunda loglama yapmak önemlidir.
            e.printStackTrace()
            return null
        }
    }
}