package com.example.payday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

// Arayüzün ihtiyaç duyacağı tüm verileri tutan bir state sınıfı
data class PaydayUiState(
    val daysLeftText: String = "--",
    val daysLeftSuffix: String = "",
    val isPayday: Boolean = false,
    val accumulatedAmountText: String = "₺0,00",
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val accumulatedSavingsForGoals: Double = 0.0,
    val areGoalsVisible: Boolean = false,
    val isWeekendAdjustmentEnabled: Boolean = false
)

class PaydayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaydayRepository(application)

    private val _uiState = MutableLiveData<PaydayUiState>()
    val uiState: LiveData<PaydayUiState> = _uiState

    // Tek seferlik olaylar için (örn: dialog göster)
    private val _showPaydayDialog = MutableLiveData<Event<Unit>>()
    val showPaydayDialog: LiveData<Event<Unit>> = _showPaydayDialog

    private var currentGoals: MutableList<SavingsGoal> = mutableListOf()

    init {
        currentGoals = repository.getGoals()
        updateCountdown()
    }

    fun updateCountdown(forceUpdate: Boolean = false) {
        val context = getApplication<Application>().applicationContext
        val result = PaydayCalculator.calculate(context)

        val accumulatedSavingsForGoals = if (result != null && result.totalDaysInCycle > 0) {
            val monthlySavings = repository.getMonthlySavingsAmount().toDouble()
            val daysPassed = result.totalDaysInCycle - result.daysLeft
            val dailySavingsRate = monthlySavings / result.totalDaysInCycle
            dailySavingsRate * daysPassed
        } else {
            0.0
        }

        val newState = if (result == null) {
            PaydayUiState(
                daysLeftText = context.getString(R.string.day_not_set_placeholder),
                daysLeftSuffix = context.getString(R.string.welcome_message),
                isWeekendAdjustmentEnabled = repository.isWeekendAdjustmentEnabled()
            )
        } else {
            PaydayUiState(
                daysLeftText = if (result.isPayday) "" else result.daysLeft.toString(),
                daysLeftSuffix = if (result.isPayday) context.getString(R.string.payday_is_today) else context.getString(R.string.days_left_suffix),
                isPayday = result.isPayday,
                accumulatedAmountText = formatCurrency(result.accumulatedAmount),
                // currentGoals listesinin bir kopyasını atıyoruz ki LiveData değişiklikleri algılasın
                savingsGoals = currentGoals.toList(),
                accumulatedSavingsForGoals = accumulatedSavingsForGoals,
                areGoalsVisible = currentGoals.isNotEmpty(),
                isWeekendAdjustmentEnabled = repository.isWeekendAdjustmentEnabled()
            )
        }
        _uiState.value = newState
    }

    // --- Ayarları Kaydetme ---

    fun savePayday(day: Int) {
        repository.savePayday(day)
        updateCountdown(true)
    }

    fun saveBiWeeklyReferenceDate(date: LocalDate) {
        repository.saveBiWeeklyReferenceDate(date)
        updateCountdown(true)
    }

    fun savePayPeriod(period: PayPeriod){
        repository.savePayPeriod(period)
        // Genellikle periyot seçiminden sonra gün/tarih seçimi gelir,
        // bu yüzden UI'da o akışı tetiklemek gerekir.
        // MainActivity'deki showDynamicPaydaySelectionDialog'u çağıracağız.
        _showPaydayDialog.value = Event(Unit)
        updateCountdown(true)
    }

    fun saveWeekendAdjustment(isEnabled: Boolean) {
        repository.saveWeekendAdjustmentSetting(isEnabled)
        updateCountdown(true)
    }

    fun saveSalary(salary: Long) {
        repository.saveSalary(salary)
        updateCountdown(true)
    }

    fun saveMonthlySavings(amount: Long) {
        repository.saveMonthlySavings(amount)
        updateCountdown(true)
    }

    fun addOrUpdateGoal(name: String, amount: Double, existingGoal: SavingsGoal? = null) {
        if (name.isNotBlank() && amount > 0) {
            if (existingGoal == null) {
                currentGoals.add(SavingsGoal(name = name, targetAmount = amount))
            } else {
                val index = currentGoals.indexOfFirst { it.id == existingGoal.id }
                if (index != -1) {
                    currentGoals[index] = existingGoal.copy(name = name, targetAmount = amount)
                }
            }
            repository.saveGoals(currentGoals)
            updateCountdown(true)
        }
    }

    // Yeni silme fonksiyonu
    fun deleteGoal(goal: SavingsGoal) {
        currentGoals.remove(goal)
        repository.saveGoals(currentGoals)
        updateCountdown(true) // UI'ı güncellemek için geri sayımı tetikle
    }

    fun loadPreferences() {
        // Bu fonksiyon, SettingsActivity'den dönüşte tüm tercihleri yeniden yüklemek için kullanılabilir.
        // updateCountdown zaten repository'den verileri çektiği için bu yeterli olacaktır.
        currentGoals = repository.getGoals() // Hedefleri de yeniden yükle
        updateCountdown(true)
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
        return format.format(amount)
    }
}


// LiveData'nın olayları birden çok kez tetiklemesini önleyen yardımcı sınıf
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
}