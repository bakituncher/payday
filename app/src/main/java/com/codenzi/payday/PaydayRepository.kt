package com.codenzi.payday

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "PaydayPrefs")

class PaydayRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.dataStore
    private val transactionDao = AppDatabase.getDatabase(context.applicationContext).transactionDao()
    private val googleDriveManager = GoogleDriveManager(context)

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
        val KEY_LAST_PROCESSED_CYCLE_END_DATE = stringPreferencesKey("last_processed_cycle_end_date")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_CONSECUTIVE_POSITIVE_CYCLES = intPreferencesKey("consecutive_positive_cycles")
        val KEY_SHOW_SIGN_IN_PROMPT = booleanPreferencesKey("show_sign_in_prompt")
        val KEY_SHOW_LOGIN_ON_START = booleanPreferencesKey("show_login_on_start")
        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val KEY_LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    }

    suspend fun deleteAllUserData() = withContext(Dispatchers.IO) {
        googleDriveManager.deleteBackupFile()
        transactionDao.deleteAllTransactions()
        prefs.edit { preferences ->
            preferences.clear()
        }
    }

    // GETTERS
    fun getPayPeriod(): Flow<PayPeriod> = prefs.data.map { PayPeriod.valueOf(it[KEY_PAY_PERIOD] ?: PayPeriod.MONTHLY.name) }
    fun getPaydayValue(): Flow<Int> = prefs.data.map { it[KEY_PAYDAY_VALUE] ?: -1 }
    fun getBiWeeklyRefDateString(): Flow<String?> = prefs.data.map { it[KEY_BI_WEEKLY_REF_DATE] }
    fun getSalaryAmount(): Flow<Long> = prefs.data.map { it[KEY_SALARY] ?: 0L }
    fun isWeekendAdjustmentEnabled(): Flow<Boolean> = prefs.data.map { it[KEY_WEEKEND_ADJUSTMENT] ?: false }
    fun getMonthlySavingsAmount(): Flow<Long> = prefs.data.map { it[KEY_MONTHLY_SAVINGS] ?: 0L }
    fun isOnboardingComplete(): Flow<Boolean> = prefs.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    fun getFirstLaunchDate(): Flow<String?> = prefs.data.map { it[KEY_FIRST_LAUNCH_DATE] }
    fun getLastProcessedCycleEndDate(): Flow<String?> = prefs.data.map { it[KEY_LAST_PROCESSED_CYCLE_END_DATE] }
    fun getTheme(): Flow<String> = prefs.data.map { it[KEY_THEME] ?: "System" }
    fun getConsecutivePositiveCycles(): Flow<Int?> = prefs.data.map { it[KEY_CONSECUTIVE_POSITIVE_CYCLES] }
    fun getUnlockedAchievementIds(): Flow<Set<String>> = prefs.data.map { it[KEY_UNLOCKED_ACHIEVEMENTS] ?: emptySet() }
    fun shouldShowSignInPrompt(): Flow<Boolean> = prefs.data.map { it[KEY_SHOW_SIGN_IN_PROMPT] ?: true }
    fun shouldShowLoginOnStart(): Flow<Boolean> = prefs.data.map { it[KEY_SHOW_LOGIN_ON_START] ?: true }
    fun isAutoBackupEnabled(): Flow<Boolean> = prefs.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: false }
    fun getLastBackupTimestamp(): Flow<Long> = prefs.data.map { it[KEY_LAST_BACKUP_TIMESTAMP] ?: 0L }

    // SETTERS
    suspend fun savePayPeriod(payPeriod: PayPeriod) = prefs.edit { it[KEY_PAY_PERIOD] = payPeriod.name }
    suspend fun saveTheme(theme: String) = prefs.edit { it[KEY_THEME] = theme }
    suspend fun savePayday(day: Int) = prefs.edit { it[KEY_PAYDAY_VALUE] = day; it.remove(KEY_BI_WEEKLY_REF_DATE) }
    suspend fun saveSalary(salary: Long) = prefs.edit { it[KEY_SALARY] = salary }
    suspend fun saveGoals(goals: List<SavingsGoal>) = prefs.edit { it[KEY_SAVINGS_GOALS] = gson.toJson(goals) }
    suspend fun setOnboardingComplete(isComplete: Boolean) = prefs.edit { it[KEY_ONBOARDING_COMPLETE] = isComplete }
    suspend fun saveBiWeeklyReferenceDate(date: LocalDate) = prefs.edit { it[KEY_BI_WEEKLY_REF_DATE] = date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    suspend fun saveMonthlySavings(amount: Long) = prefs.edit { it[KEY_MONTHLY_SAVINGS] = amount }
    suspend fun setFirstLaunchDate(date: LocalDate) = prefs.edit { it[KEY_FIRST_LAUNCH_DATE] = date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    suspend fun saveLastProcessedCycleEndDate(date: LocalDate) = prefs.edit { it[KEY_LAST_PROCESSED_CYCLE_END_DATE] = date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    suspend fun saveConsecutivePositiveCycles(count: Int) = prefs.edit { it[KEY_CONSECUTIVE_POSITIVE_CYCLES] = count }
    suspend fun setSignInPrompt(shouldShow: Boolean) = prefs.edit { it[KEY_SHOW_SIGN_IN_PROMPT] = shouldShow }
    suspend fun setShowLoginOnStart(shouldShow: Boolean) { prefs.edit { it[KEY_SHOW_LOGIN_ON_START] = shouldShow } }
    suspend fun setAutoBackupEnabled(isEnabled: Boolean) { prefs.edit { it[KEY_AUTO_BACKUP_ENABLED] = isEnabled } }

    private suspend fun saveLastBackupTimestamp(timestamp: Long) {
        prefs.edit { it[KEY_LAST_BACKUP_TIMESTAMP] = timestamp }
    }

    suspend fun unlockAchievement(achievementId: String) {
        prefs.edit { preferences ->
            val unlocked = preferences[KEY_UNLOCKED_ACHIEVEMENTS]?.toMutableSet() ?: mutableSetOf()
            if (unlocked.add(achievementId)) {
                preferences[KEY_UNLOCKED_ACHIEVEMENTS] = unlocked
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun performSmartBackup() {
        if (isAutoBackupEnabled().first() && GoogleSignIn.getLastSignedInAccount(context) != null) {
            val lastBackupTimestamp = getLastBackupTimestamp().first()
            val fifteenMinutesInMillis = TimeUnit.MINUTES.toMillis(15)

            if ((System.currentTimeMillis() - lastBackupTimestamp) > fifteenMinutesInMillis) {
                try {
                    val backupData = getAllDataForBackup()
                    val backupJson = gson.toJson(backupData)
                    googleDriveManager.uploadFileContent(backupJson)
                    saveLastBackupTimestamp(System.currentTimeMillis())
                } catch (e: Exception) {
                    // Hata durumunda bir sonraki denemeyi engellememek için sessiz kal.
                }
            }
        }
    }

    fun getGoals(): Flow<MutableList<SavingsGoal>> {
        return prefs.data.map { preferences ->
            val jsonGoals = preferences[KEY_SAVINGS_GOALS]
            if (jsonGoals != null) {
                try {
                    val type = object : TypeToken<MutableList<SavingsGoal>>() {}.type
                    gson.fromJson(jsonGoals, type)
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
        }
    }

    fun getTransactionsBetweenDates(startDate: Date, endDate: Date): Flow<List<Transaction>> = transactionDao.getTransactionsBetweenDates(startDate, endDate)
    fun getTotalExpensesBetweenDates(startDate: Date, endDate: Date): Flow<Double?> = transactionDao.getTotalExpensesBetweenDates(startDate, endDate)
    fun getSpendingByCategoryBetweenDates(startDate: Date, endDate: Date): Flow<List<CategorySpending>> = transactionDao.getSpendingByCategoryBetweenDates(startDate, endDate)
    fun getRecurringTransactionTemplates(): Flow<List<Transaction>> = transactionDao.getRecurringTransactionTemplates()
    fun getDailySpendingForChart(startDate: Date, endDate: Date): Flow<List<DailySpending>> = transactionDao.getDailySpendingForChart(startDate, endDate)
    fun getMonthlySpendingForCategory(categoryId: Int): Flow<List<MonthlyCategorySpending>> = transactionDao.getMonthlySpendingForCategory(categoryId)
    fun getAllTransactionsForAchievements(): Flow<List<Transaction>> = transactionDao.getAllTransactionsFlow()
    suspend fun insertTransaction(transaction: Transaction) = transactionDao.insert(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)

    suspend fun getAllDataForBackup(): BackupData = withContext(Dispatchers.IO) {
        val allTransactions = transactionDao.getAllTransactions()
        val goals = getGoals().first()
        val settingsMap = mutableMapOf<String, String?>()
        val prefsSnapshot = prefs.data.first()
        prefsSnapshot.asMap().forEach { (key, value) ->
            settingsMap[key.name] = value.toString()
        }
        return@withContext BackupData(
            transactions = allTransactions,
            savingsGoals = goals,
            settings = settingsMap
        )
    }

    suspend fun restoreDataFromBackup(backupData: BackupData) = withContext(Dispatchers.IO) {
        transactionDao.deleteAllTransactions()
        backupData.transactions.forEach { transactionDao.insert(it) }

        prefs.edit { preferences ->
            val currentAutoBackupSetting = preferences[KEY_AUTO_BACKUP_ENABLED]
            preferences.clear()

            backupData.settings.forEach { (key, value) ->
                if (key != KEY_SAVINGS_GOALS.name && key != KEY_AUTO_BACKUP_ENABLED.name) {
                    when (key) {
                        KEY_PAYDAY_VALUE.name, KEY_CONSECUTIVE_POSITIVE_CYCLES.name -> preferences[intPreferencesKey(key)] = value?.toIntOrNull() ?: 0
                        KEY_WEEKEND_ADJUSTMENT.name, KEY_ONBOARDING_COMPLETE.name, KEY_SHOW_LOGIN_ON_START.name, KEY_SHOW_SIGN_IN_PROMPT.name -> preferences[booleanPreferencesKey(key)] = value?.toBoolean() ?: false
                        KEY_SALARY.name, KEY_MONTHLY_SAVINGS.name -> preferences[longPreferencesKey(key)] = value?.toLongOrNull() ?: 0L
                        KEY_PAY_PERIOD.name, KEY_BI_WEEKLY_REF_DATE.name, KEY_FIRST_LAUNCH_DATE.name, KEY_LAST_PROCESSED_CYCLE_END_DATE.name, KEY_THEME.name -> {
                            if (value != null) preferences[stringPreferencesKey(key)] = value
                        }
                        KEY_UNLOCKED_ACHIEVEMENTS.name -> {
                            if (value != null) {
                                val unlockedSet = value.removeSurrounding("[", "]")
                                    .split(", ")
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                                preferences[stringSetPreferencesKey(key)] = unlockedSet
                            }
                        }
                    }
                }
            }
            if (backupData.savingsGoals.isNotEmpty()) {
                val goalsJson = gson.toJson(backupData.savingsGoals)
                preferences[KEY_SAVINGS_GOALS] = goalsJson
            }
            if (currentAutoBackupSetting != null) {
                preferences[KEY_AUTO_BACKUP_ENABLED] = currentAutoBackupSetting
            }
        }
    }
}
