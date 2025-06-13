// Dosya: app/src/main/java/com/example/payday/PaydayRepository.kt

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

    // SharedPreferences Anahtarları
    private val paydayKey = "PaydayOfMonth"
    private val weekendAdjustmentKey = "WeekendAdjustmentEnabled"
    private val salaryKey = "SalaryAmount"
    private val payPeriodKey = "PayPeriod"
    private val biWeeklyRefDateKey = "BiWeeklyRefDate"
    private val savingsGoalsKey = "SavingsGoals"
    private val monthlySavingsAmountKey = "MonthlySavingsAmount"

    fun getPaydayResult(): PaydayResult? {
        // Hesaplama mantığı zaten merkezi olduğu için direkt calculator'ı çağırabiliriz.
        // `calculate` metodu context'e ihtiyaç duyduğu için onu doğrudan ViewModel'den çağırmak daha mantıklı.
        // Bu repo şimdilik sadece veri kaydetme/okuma yapsın.
        // Bu yüzden bu metodu şimdilik boş bırakıp doğrudan ViewModel'de handle edelim.
        // Ya da Calculator'a context yerine ayarları parametre olarak geçebiliriz. Şimdilik basit tutalım.
        return null // ViewModel'de ele alınacak
    }

    fun savePayday(day: Int) {
        prefs.edit {
            putInt(paydayKey, day)
            remove(biWeeklyRefDateKey) // Çakışmayı önle
        }
    }

    fun savePayPeriod(payPeriod: PayPeriod) {
        prefs.edit { putString(payPeriodKey, payPeriod.name) }
    }

    fun saveBiWeeklyReferenceDate(date: LocalDate) {
        prefs.edit {
            putString(biWeeklyRefDateKey, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            remove(paydayKey) // Çakışmayı önle
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

    fun getGoals(): MutableList<SavingsGoal> {
        val jsonGoals = prefs.getString(savingsGoalsKey, null)
        return if (jsonGoals != null) {
            val type = object : TypeToken<MutableList<SavingsGoal>>() {}.type
            gson.fromJson(jsonGoals, type)
        } else {
            mutableListOf()
        }
    }

    // Ayarları okumak için yardımcı fonksiyonlar
    fun isWeekendAdjustmentEnabled(): Boolean = prefs.getBoolean(weekendAdjustmentKey, false)
    fun getMonthlySavingsAmount(): Long = prefs.getLong(monthlySavingsAmountKey, 0L)
    fun getCurrentSalary(): Long = prefs.getLong(salaryKey, 0L)
}