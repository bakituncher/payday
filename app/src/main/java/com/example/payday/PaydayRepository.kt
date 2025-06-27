package com.example.payday

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "PaydayPrefs")

class PaydayRepository(context: Context) {

    private val gson = Gson()
    private val prefs = context.dataStore
    private val transactionDao = AppDatabase.getDatabase(context.applicationContext).transactionDao()

    companion object {
        val KEY_PAYDAY_VALUE = intPreferencesKey("payday")
        val KEY_WEEKEND_ADJUSTMENT = booleanPreferencesKey("weekend_adjustment")
        val KEY_SALARY = longPreferencesKey("salary")
        val KEY_PAY_PERIOD = stringPreferencesKey("pay_period")
        val KEY_BI_WEEKLY_REF_DATE = stringPreferencesKey("bi_weekly_ref_date")
        val KEY_SAVINGS_GOALS = stringPreferencesKey("savings_goals")
        val KEY_MONTHLY_SAVINGS = longPreferencesKey("monthly_savings")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_UNLOCKED_ACHIEVEMENTS = stringSetPreferencesKey("unlocked_achievements")
        val KEY_FIRST_LAUNCH_DATE = stringPreferencesKey("first_launch_date")
    }

    // --- Okuma Fonksiyonları (Flow<T> döndürür) ---
    fun getPayPeriod(): Flow<PayPeriod> = prefs.data.map { PayPeriod.valueOf(it[KEY_PAY_PERIOD] ?: PayPeriod.MONTHLY.name) }
    fun getPaydayValue(): Flow<Int> = prefs.data.map { it[KEY_PAYDAY_VALUE] ?: -1 }
    fun getBiWeeklyRefDateString(): Flow<String?> = prefs.data.map { it[KEY_BI_WEEKLY_REF_DATE] }
    fun getSalaryAmount(): Flow<Long> = prefs.data.map { it[KEY_SALARY] ?: 0L }
    fun isWeekendAdjustmentEnabled(): Flow<Boolean> = prefs.data.map { it[KEY_WEEKEND_ADJUSTMENT] ?: false }
    fun getMonthlySavingsAmount(): Flow<Long> = prefs.data.map { it[KEY_MONTHLY_SAVINGS] ?: 0L }
    fun isOnboardingComplete(): Flow<Boolean> = prefs.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    fun getFirstLaunchDate(): Flow<String?> = prefs.data.map { it[KEY_FIRST_LAUNCH_DATE] }

    fun getGoals(): Flow<MutableList<SavingsGoal>> {
        return prefs.data.map { preferences ->
            val jsonGoals = preferences[KEY_SAVINGS_GOALS]
            if (jsonGoals != null) {
                val type = object : TypeToken<MutableList<SavingsGoal>>() {}.type
                gson.fromJson(jsonGoals, type)
            } else {
                mutableListOf()
            }
        }
    }

    // Başarım ID'lerini Flow olarak oku
    fun getUnlockedAchievementIds(): Flow<Set<String>> {
        return prefs.data.map { it[KEY_UNLOCKED_ACHIEVEMENTS] ?: emptySet() }
    }

    // --- Yazma Fonksiyonları (suspend) ---
    suspend fun savePayPeriod(payPeriod: PayPeriod) = prefs.edit { it[KEY_PAY_PERIOD] = payPeriod.name }
    suspend fun savePayday(day: Int) = prefs.edit { it[KEY_PAYDAY_VALUE] = day; it.remove(KEY_BI_WEEKLY_REF_DATE) }
    suspend fun saveSalary(salary: Long) = prefs.edit { it[KEY_SALARY] = salary }
    suspend fun saveGoals(goals: List<SavingsGoal>) = prefs.edit { it[KEY_SAVINGS_GOALS] = gson.toJson(goals) }
    suspend fun setOnboardingComplete(isComplete: Boolean) = prefs.edit { it[KEY_ONBOARDING_COMPLETE] = isComplete }
    suspend fun saveBiWeeklyReferenceDate(date: LocalDate) = prefs.edit { it[KEY_BI_WEEKLY_REF_DATE] = date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    suspend fun saveMonthlySavings(amount: Long) = prefs.edit { it[KEY_MONTHLY_SAVINGS] = amount }
    suspend fun setFirstLaunchDate(date: LocalDate) = prefs.edit { it[KEY_FIRST_LAUNCH_DATE] = date.format(DateTimeFormatter.ISO_LOCAL_DATE) }

    // Başarım kilidini aç (suspend)
    suspend fun unlockAchievement(achievementId: String) {
        prefs.edit { preferences ->
            val unlocked = preferences[KEY_UNLOCKED_ACHIEVEMENTS]?.toMutableSet() ?: mutableSetOf()
            if (unlocked.add(achievementId)) {
                preferences[KEY_UNLOCKED_ACHIEVEMENTS] = unlocked
            }
        }
    }


    // --- Room Veritabanı Fonksiyonları (Değişiklik Yok) ---
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()
    fun getTotalExpenses(): Flow<Double?> = transactionDao.getTotalExpenses()
    suspend fun insertTransaction(transaction: Transaction) = transactionDao.insert(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)
    fun getSpendingByCategory(): Flow<List<CategorySpending>> = transactionDao.getSpendingByCategory()
}