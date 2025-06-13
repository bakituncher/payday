package com.example.payday

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        val paydayOfMonth = prefs.getInt("PaydayOfMonth", -1)
        val weekendAdjustmentEnabled = prefs.getBoolean("WeekendAdjustmentEnabled", false)
        // Read the salary amount (as Long) from SharedPreferences
        val salaryAmount = prefs.getLong("SalaryAmount", 0L)

        if (paydayOfMonth == -1) {
            return null // Return null if payday is not set
        }

        try {
            val today = LocalDate.now()

            // Find the previous payday date by checking if this month's payday has passed
            val previousPayday = if (today.dayOfMonth >= paydayOfMonth) {
                today.withDayOfMonth(paydayOfMonth)
            } else {
                today.minusMonths(1).withDayOfMonth(paydayOfMonth)
            }

            // Find the next payday date
            var nextPayday = if (today.dayOfMonth >= paydayOfMonth) {
                val nextMonth = today.plusMonths(1)
                val lastDayOfNextMonth = nextMonth.lengthOfMonth()
                nextMonth.withDayOfMonth(minOf(paydayOfMonth, lastDayOfNextMonth))
            } else {
                today.withDayOfMonth(paydayOfMonth)
            }

            // --- Weekend Adjustment Logic ---
            val originalNextPayday = nextPayday // Store the original date before adjustment
            if (weekendAdjustmentEnabled) {
                if (nextPayday.dayOfWeek == DayOfWeek.SATURDAY) {
                    nextPayday = nextPayday.minusDays(1) // Adjust to Friday
                } else if (nextPayday.dayOfWeek == DayOfWeek.SUNDAY) {
                    nextPayday = nextPayday.minusDays(2) // Adjust to Friday
                }
            }
            // --- End of Logic ---

            val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)

            // --- ACCUMULATION CALCULATION LOGIC ---
            var accumulatedAmount = 0.0
            if (salaryAmount > 0) {
                // Total number of days in the current pay cycle (from previous to original next)
                val totalDaysInCycle = ChronoUnit.DAYS.between(previousPayday, originalNextPayday)
                // Number of days passed since the last payday
                val daysPassed = ChronoUnit.DAYS.between(previousPayday, today)

                if (totalDaysInCycle > 0) {
                    val dailyRate = salaryAmount.toDouble() / totalDaysInCycle
                    accumulatedAmount = dailyRate * daysPassed
                }
            }
            // --- End of Logic ---

            return PaydayResult(
                daysLeft = daysLeft,
                isPayday = daysLeft <= 0L, // It's payday if the day is today or has been adjusted to an earlier day
                accumulatedAmount = accumulatedAmount // Include the calculated amount
            )
        } catch (e: Exception) {
            // Return null in case of an error (e.g., invalid day like Feb 30)
            return null
        }
    }
}