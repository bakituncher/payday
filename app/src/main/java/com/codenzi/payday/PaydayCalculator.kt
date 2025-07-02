package com.codenzi.payday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Date
import kotlin.math.min

data class PaydayResult(
    val daysLeft: Long,
    val isPayday: Boolean,
    val totalDaysInCycle: Long,
    val cycleStartDate: LocalDate,
    val cycleEndDate: LocalDate
)

object PaydayCalculator {

    fun calculate(
        dateToCheck: LocalDate,
        payPeriod: PayPeriod,
        paydayValue: Int,
        biWeeklyRefDateString: String?,
        weekendAdjustmentEnabled: Boolean
    ): PaydayResult? {
        try {
            val currentCyclePayday: LocalDate
            val previousCyclePayday: LocalDate

            when (payPeriod) {
                PayPeriod.MONTHLY -> {
                    if (paydayValue !in 1..31) return null

                    val thisMonthPayday = dateToCheck.withDayOfMonth(min(paydayValue, dateToCheck.month.length(dateToCheck.isLeapYear)))

                    if (dateToCheck.isAfter(thisMonthPayday)) {
                        val nextMonth = dateToCheck.plusMonths(1)
                        currentCyclePayday = nextMonth.withDayOfMonth(min(paydayValue, nextMonth.month.length(nextMonth.isLeapYear)))
                        previousCyclePayday = thisMonthPayday
                    } else {
                        currentCyclePayday = thisMonthPayday
                        val prevMonth = dateToCheck.minusMonths(1)
                        previousCyclePayday = prevMonth.withDayOfMonth(min(paydayValue, prevMonth.month.length(prevMonth.isLeapYear)))
                    }
                }
                PayPeriod.WEEKLY -> {
                    if (paydayValue !in 1..7) return null
                    val payDayOfWeek = DayOfWeek.of(paydayValue)
                    currentCyclePayday = dateToCheck.with(TemporalAdjusters.nextOrSame(payDayOfWeek))
                    previousCyclePayday = currentCyclePayday.minusWeeks(1)
                }
                PayPeriod.BI_WEEKLY -> {
                    val referenceDate = LocalDate.parse(biWeeklyRefDateString) ?: return null
                    var tempPayday = referenceDate
                    while (tempPayday.isBefore(dateToCheck)) {
                        tempPayday = tempPayday.plusDays(14)
                    }
                    currentCyclePayday = tempPayday
                    previousCyclePayday = currentCyclePayday.minusDays(14)
                }
            }

            var adjustedPayday = currentCyclePayday
            if (weekendAdjustmentEnabled) {
                if (adjustedPayday.dayOfWeek == DayOfWeek.SATURDAY) adjustedPayday = adjustedPayday.minusDays(1)
                if (adjustedPayday.dayOfWeek == DayOfWeek.SUNDAY) adjustedPayday = adjustedPayday.minusDays(2)
            }

            val isPaydayForCheckedDate = dateToCheck.isEqual(adjustedPayday)
            val daysLeft = if(isPaydayForCheckedDate) 0 else ChronoUnit.DAYS.between(dateToCheck, adjustedPayday)

            val cycleStartDate = previousCyclePayday
            val cycleEndDate = currentCyclePayday.minusDays(1)

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = isPaydayForCheckedDate,
                totalDaysInCycle = ChronoUnit.DAYS.between(previousCyclePayday, currentCyclePayday),
                cycleStartDate = cycleStartDate,
                cycleEndDate = cycleEndDate
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}