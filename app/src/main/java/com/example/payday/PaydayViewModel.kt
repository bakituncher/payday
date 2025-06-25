package com.example.payday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.mikephil.charting.data.Entry // YENİ IMPORT
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

// Arayüzün ihtiyaç duyacağı tüm verileri tutan state sınıfı
data class PaydayUiState(
    val daysLeftText: String = "--",
    val daysLeftSuffix: String = "",
    val isPayday: Boolean = false,
    val accumulatedAmountText: String = "₺0,00",
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val accumulatedSavingsForGoals: Double = 0.0,
    val areGoalsVisible: Boolean = false,
    val chartData: List<Entry>? = null // Grafik Kütüphanesi için veri
)

// Olayların (tek seferlik işlemler) yönetimi için yardımcı sınıf
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set
    fun getContentIfNotHandled(): T? = if (hasBeenHandled) null else {
        hasBeenHandled = true
        content
    }
}

class PaydayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaydayRepository(application)
    private val context = application.applicationContext

    private val _uiState = MutableLiveData<PaydayUiState>()
    val uiState: LiveData<PaydayUiState> = _uiState

    // Tek seferlik olaylar için (örn: dialog göster)
    private val _events = MutableLiveData<Event<String>>()
    val events: LiveData<Event<String>> = _events

    // Widget güncellemesini tetiklemek için yeni bir event
    private val _widgetUpdateEvent = MutableLiveData<Event<Unit>>()
    val widgetUpdateEvent: LiveData<Event<Unit>> = _widgetUpdateEvent

    private var currentGoals: MutableList<SavingsGoal> = mutableListOf()

    init {
        loadData()
    }

    private fun loadData() {
        currentGoals = repository.getGoals()
        updateUi()

        // --- YENİ EKLENEN KISIM ---
        // Uygulamanın ilk kurulum olup olmadığını kontrol et
        val isInitialSetup = repository.getPaydayValue() == -1 && repository.getBiWeeklyRefDateString() == null
        if (isInitialSetup) {
            // Eğer ilk kurulumsa, kurulum diyalog zincirini başlat
            _events.value = Event("show_pay_period_dialog")
        }
        // --- YENİ EKLENEN KISIM SONU ---
    }

    private fun updateUi() {
        val result = PaydayCalculator.calculate(
            payPeriod = repository.getPayPeriod(),
            paydayValue = repository.getPaydayValue(),
            biWeeklyRefDateString = repository.getBiWeeklyRefDateString(),
            salaryAmount = repository.getSalaryAmount(),
            weekendAdjustmentEnabled = repository.isWeekendAdjustmentEnabled()
        )

        val accumulatedSavingsForGoals = if (result != null && result.totalDaysInCycle > 0) {
            val monthlySavings = repository.getMonthlySavingsAmount().toDouble()
            val daysPassed = result.totalDaysInCycle - result.daysLeft
            val dailySavingsRate = if (result.totalDaysInCycle > 0) monthlySavings / result.totalDaysInCycle else 0.0
            dailySavingsRate * daysPassed.coerceAtLeast(0)
        } else {
            0.0
        }

        val newState = if (result == null) {
            val isInitialSetup = repository.getPaydayValue() == -1 && repository.getBiWeeklyRefDateString() == null
            PaydayUiState(
                daysLeftText = if(isInitialSetup) context.getString(R.string.day_not_set_placeholder) else "!",
                daysLeftSuffix = if(isInitialSetup) context.getString(R.string.welcome_message) else context.getString(R.string.invalid_day_error)
            )
        } else {
            // GÜNCELLENDİ: Grafik verisini Entry listesine çevir
            val chartEntries = result.accumulationData.map { (day, amount) ->
                Entry(day.toFloat(), amount.toFloat())
            }

            PaydayUiState(
                daysLeftText = if (result.isPayday) "" else result.daysLeft.toString(),
                daysLeftSuffix = if (result.isPayday) context.getString(R.string.payday_is_today) else context.getString(R.string.days_left_suffix),
                isPayday = result.isPayday,
                accumulatedAmountText = formatCurrency(result.accumulatedAmount),
                savingsGoals = currentGoals.toList(),
                accumulatedSavingsForGoals = accumulatedSavingsForGoals,
                areGoalsVisible = currentGoals.isNotEmpty(),
                chartData = chartEntries // Yeni veriyi state'e ekle
            )
        }
        _uiState.value = newState
    }

    // --- Ayarları Kaydetme Fonksiyonları ---

    fun savePayday(day: Int) {
        repository.savePayday(day)
        updateUi()
        _widgetUpdateEvent.value = Event(Unit) // Widget güncelleme sinyali gönder
        // --- GÜNCELLENEN KISIM ---
        // Maaş günü kaydedildikten sonra maaş diyalogunu göster
        _events.value = Event("show_salary_dialog")
        // --- GÜNCELLENEN KISIM SONU ---
    }

    fun saveBiWeeklyReferenceDate(date: LocalDate) {
        repository.saveBiWeeklyReferenceDate(date)
        updateUi()
        _widgetUpdateEvent.value = Event(Unit) // Widget güncelleme sinyali gönder
        // --- GÜNCELLENEN KISIM ---
        // Maaş günü kaydedildikten sonra maaş diyalogunu göster
        _events.value = Event("show_salary_dialog")
        // --- GÜNCELLENEN KISIM SONU ---
    }

    fun savePayPeriod(period: PayPeriod){
        repository.savePayPeriod(period)
        _events.value = Event("show_dynamic_payday_selection")
        updateUi()
        _widgetUpdateEvent.value = Event(Unit) // Widget güncelleme sinyali gönder
    }

    fun saveSalary(salary: Long) {
        repository.saveSalary(salary)
        updateUi()
        _widgetUpdateEvent.value = Event(Unit) // Widget güncelleme sinyali gönder
        // --- GÜNCELLENEN KISIM ---
        // Maaş da kaydedildikten sonra tasarruf tutarı diyalogunu göster
        _events.value = Event("show_monthly_savings_dialog")
        // --- GÜNCELLENEN KISIM SONU ---
    }

    fun saveMonthlySavings(amount: Long) {
        repository.saveMonthlySavings(amount)
        updateUi()
    }

    fun addOrUpdateGoal(name: String, amount: Double, existingGoalId: String?) {
        if (name.isNotBlank() && amount > 0) {
            if (existingGoalId == null) {
                currentGoals.add(SavingsGoal(name = name, targetAmount = amount))
            } else {
                val index = currentGoals.indexOfFirst { it.id == existingGoalId }
                if (index != -1) {
                    currentGoals[index] = currentGoals[index].copy(name = name, targetAmount = amount)
                }
            }
            repository.saveGoals(currentGoals)
            updateUi()
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        currentGoals.remove(goal)
        repository.saveGoals(currentGoals)
        updateUi()
    }

    // SettingsActivity'den dönüldüğünde çağrılır
    fun onSettingsResult() {
        loadData()
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(amount)
    }
}
