package com.example.payday

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PaydayRepository(context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("PaydayPrefs", Context.MODE_PRIVATE)
    private val transactionDao = AppDatabase.getDatabase(context.applicationContext).transactionDao()

    companion object {
        const val KEY_PAYDAY_VALUE = "payday"
        const val KEY_WEEKEND_ADJUSTMENT = "weekend_adjustment"
        const val KEY_SALARY = "salary"
        const val KEY_PAY_PERIOD = "pay_period"
        const val KEY_BI_WEEKLY_REF_DATE = "bi_weekly_ref_date"
        const val KEY_SAVINGS_GOALS = "savings_goals"
        const val KEY_MONTHLY_SAVINGS = "monthly_savings"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_UNLOCKED_ACHIEVEMENTS = "unlocked_achievements"
        const val KEY_FIRST_LAUNCH_DATE = "first_launch_date"
    }

    // First Launch Date Functions
    fun getFirstLaunchDate(): String? {
        return prefs.getString(KEY_FIRST_LAUNCH_DATE, null)
    }

    fun setFirstLaunchDate(date: LocalDate) {
        prefs.edit {
            putString(KEY_FIRST_LAUNCH_DATE, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
    }

    // Transaction Functions
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()
    fun getTotalExpenses(): Flow<Double?> = transactionDao.getTotalExpenses()
    suspend fun insertTransaction(transaction: Transaction) = transactionDao.insert(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)
    fun getSpendingByCategory(): Flow<List<CategorySpending>> = transactionDao.getSpendingByCategory()


    // Settings Functions
    fun savePayday(day: Int) {
        prefs.edit {
            putInt(KEY_PAYDAY_VALUE, day)
            remove(KEY_BI_WEEKLY_REF_DATE)
        }
    }

    fun savePayPeriod(payPeriod: PayPeriod) {
        prefs.edit { putString(KEY_PAY_PERIOD, payPeriod.name) }
    }

    fun saveBiWeeklyReferenceDate(date: LocalDate) {
        prefs.edit {
            putString(KEY_BI_WEEKLY_REF_DATE, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            remove(KEY_PAYDAY_VALUE)
        }
    }

    fun saveSalary(salary: Long) {
        prefs.edit { putLong(KEY_SALARY, salary) }
    }

    fun saveMonthlySavings(amount: Long) {
        prefs.edit { putLong(KEY_MONTHLY_SAVINGS, amount) }
    }

    fun getPaydayValue(): Int = prefs.getInt(KEY_PAYDAY_VALUE, -1)
    fun getPayPeriod(): PayPeriod = PayPeriod.valueOf(prefs.getString(KEY_PAY_PERIOD, PayPeriod.MONTHLY.name)!!)
    fun getBiWeeklyRefDateString(): String? = prefs.getString(KEY_BI_WEEKLY_REF_DATE, null)
    fun getSalaryAmount(): Long = prefs.getLong(KEY_SALARY, 0L)
    fun isWeekendAdjustmentEnabled(): Boolean = prefs.getBoolean(KEY_WEEKEND_ADJUSTMENT, false)
    fun getMonthlySavingsAmount(): Long = prefs.getLong(KEY_MONTHLY_SAVINGS, 0L)


    // Savings Goal Functions
    fun saveGoals(goals: List<SavingsGoal>) {
        val jsonGoals = gson.toJson(goals)
        prefs.edit { putString(KEY_SAVINGS_GOALS, jsonGoals) }
    }

    fun getGoals(): MutableList<SavingsGoal> {
        val jsonGoals = prefs.getString(KEY_SAVINGS_GOALS, null)
        return if (jsonGoals != null) {
            val type = object : TypeToken<MutableList<SavingsGoal>>() {}.type
            gson.fromJson(jsonGoals, type)
        } else {
            mutableListOf()
        }
    }

    // Achievement Functions
    fun getUnlockedAchievementIds(): Set<String> {
        return prefs.getStringSet(KEY_UNLOCKED_ACHIEVEMENTS, emptySet()) ?: emptySet()
    }

    fun unlockAchievement(achievementId: String) {
        val unlocked = getUnlockedAchievementIds().toMutableSet()
        if (unlocked.add(achievementId)) {
            prefs.edit { putStringSet(KEY_UNLOCKED_ACHIEVEMENTS, unlocked) }
        }
    }

    // Onboarding Functions
    fun setOnboardingComplete(isComplete: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, isComplete) }
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
}
