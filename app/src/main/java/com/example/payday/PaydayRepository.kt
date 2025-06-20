package com.example.payday

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PaydayRepository(context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("PaydayPrefs", Context.MODE_PRIVATE)

    private val paydayKey = "PaydayOfMonth"
    private val weekendAdjustmentKey = "WeekendAdjustmentEnabled"
    private val salaryKey = "SalaryAmount"
    private val payPeriodKey = "PayPeriod"
    private val biWeeklyRefDateKey = "BiWeeklyRefDate"
    private val savingsGoalsKey = "SavingsGoals"
    private val monthlySavingsAmountKey = "MonthlySavingsAmount"

    fun savePayday(day: Int) {
        prefs.edit {
            putInt(paydayKey, day)
            remove(biWeeklyRefDateKey)
        }
    }

    fun savePayPeriod(payPeriod: PayPeriod) {
        prefs.edit { putString(payPeriodKey, payPeriod.name) }
    }

    fun saveBiWeeklyReferenceDate(date: LocalDate) {
        prefs.edit {
            putString(biWeeklyRefDateKey, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            remove(paydayKey)
        }
    }

    fun saveWeekendAdjustmentSetting(isEnabled: Boolean) {
        prefs.edit { putBoolean(weekendAdjustmentKey, isEnabled) }
    }

    fun saveSalary(salary: Long) {
        prefs.edit { putLong(salaryKey, salary) }
    }

    fun saveMonthlySavings(amount: Long) {
        prefs.edit { putLong(monthlySavingsAmountKey, amount) }
    }

    fun saveGoals(goals: List<SavingsGoal>) {
        val jsonGoals = gson.toJson(goals)
        prefs.edit { putString(savingsGoalsKey, jsonGoals) }
    }

    // Hesaplama için gerekli tüm ayarları tek tek getiren fonksiyonlar
    fun getPaydayValue(): Int = prefs.getInt(paydayKey, -1)
    fun getPayPeriod(): PayPeriod = PayPeriod.valueOf(prefs.getString(payPeriodKey, PayPeriod.MONTHLY.name)!!)
    fun getBiWeeklyRefDateString(): String? = prefs.getString(biWeeklyRefDateKey, null)
    fun getSalaryAmount(): Long = prefs.getLong(salaryKey, 0L)
    fun isWeekendAdjustmentEnabled(): Boolean = prefs.getBoolean(weekendAdjustmentKey, false)
    fun getMonthlySavingsAmount(): Long = prefs.getLong(monthlySavingsAmountKey, 0L)
    fun getGoals(): MutableList<SavingsGoal> {
        val jsonGoals = prefs.getString(savingsGoalsKey, null)
        return if (jsonGoals != null) {
            val type = object : TypeToken<MutableList<SavingsGoal>>() {}.type
            gson.fromJson(jsonGoals, type)
        } else {
            mutableListOf()
        }
    }
}