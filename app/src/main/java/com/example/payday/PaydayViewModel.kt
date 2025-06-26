package com.example.payday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*

// PaydayUiState'i finansal özeti içerecek şekilde güncelledik.
data class PaydayUiState(
    val daysLeftText: String = "--",
    val daysLeftSuffix: String = "",
    val isPayday: Boolean = false,
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val accumulatedSavingsForGoals: Double = 0.0,
    val areGoalsVisible: Boolean = false,
    val areTransactionsVisible: Boolean = false,
    // Yeni Finansal Özet Verileri
    val incomeText: String = "₺0,00",
    val expensesText: String = "₺0,00",
    val remainingText: String = "₺0,00"
)

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

    private val _widgetUpdateEvent = MutableLiveData<Event<Unit>>()
    val widgetUpdateEvent: LiveData<Event<Unit>> = _widgetUpdateEvent

    private val _newAchievementEvent = MutableLiveData<Event<Achievement>>()
    val newAchievementEvent: LiveData<Event<Achievement>> = _newAchievementEvent

    private var currentGoals: MutableList<SavingsGoal> = mutableListOf()
    val allTransactions: LiveData<List<Transaction>> = repository.getAllTransactions().asLiveData()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            currentGoals = repository.getGoals()
            // Veritabanı ve ayarlardan gelen tüm verileri birleştirerek UI'yı güncelliyoruz.
            repository.getTotalExpenses().collectLatest { totalExpenses ->
                updateUi(totalExpenses ?: 0.0)
            }
        }
    }

    fun insertTransaction(name: String, amount: Double) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val transaction = Transaction(name = name, amount = amount, date = Date())
            repository.insertTransaction(transaction)
        }
    }

    private fun updateUi(totalExpenses: Double) {
        viewModelScope.launch {
            val result = PaydayCalculator.calculate(
                payPeriod = repository.getPayPeriod(),
                paydayValue = repository.getPaydayValue(),
                biWeeklyRefDateString = repository.getBiWeeklyRefDateString(),
                salaryAmount = repository.getSalaryAmount(),
                weekendAdjustmentEnabled = repository.isWeekendAdjustmentEnabled()
            )

            val salary = repository.getSalaryAmount().toDouble()
            val remainingAmount = salary - totalExpenses

            val accumulatedSavingsForGoals = if (result != null && result.totalDaysInCycle > 0) {
                val monthlySavings = repository.getMonthlySavingsAmount().toDouble()
                val daysPassed = result.totalDaysInCycle - result.daysLeft
                val dailySavingsRate = if (result.totalDaysInCycle > 0) monthlySavings / result.totalDaysInCycle else 0.0
                dailySavingsRate * daysPassed.coerceAtLeast(0)
            } else { 0.0 }

            result?.let {
                val unlockedIds = repository.getUnlockedAchievementIds()
                if (it.isPayday && !unlockedIds.contains("PAYDAY_HYPE")) {
                    repository.unlockAchievement("PAYDAY_HYPE")
                    AchievementsManager.getAllAchievements().find { ach -> ach.id == "PAYDAY_HYPE" }?.let { ach ->
                        _newAchievementEvent.postValue(Event(ach))
                    }
                }
                if (accumulatedSavingsForGoals >= 1000 && !unlockedIds.contains("SAVER_LV1")) {
                    repository.unlockAchievement("SAVER_LV1")
                    AchievementsManager.getAllAchievements().find { ach -> ach.id == "SAVER_LV1" }?.let { ach ->
                        _newAchievementEvent.postValue(Event(ach))
                    }
                }
            }

            val transactionsList = allTransactions.value ?: emptyList()
            val newState = if (result == null) {
                PaydayUiState(
                    daysLeftText = context.getString(R.string.day_not_set_placeholder),
                    daysLeftSuffix = context.getString(R.string.welcome_message)
                )
            } else {
                PaydayUiState(
                    daysLeftText = if (result.isPayday) "🎉" else result.daysLeft.toString(),
                    daysLeftSuffix = if (result.isPayday) context.getString(R.string.payday_is_today) else context.getString(R.string.days_left_suffix),
                    isPayday = result.isPayday,
                    savingsGoals = currentGoals.toList(),
                    accumulatedSavingsForGoals = accumulatedSavingsForGoals,
                    areGoalsVisible = currentGoals.isNotEmpty(),
                    areTransactionsVisible = transactionsList.isNotEmpty(),
                    incomeText = formatCurrency(salary),
                    expensesText = "- ${formatCurrency(totalExpenses)}",
                    remainingText = formatCurrency(remainingAmount)
                )
            }
            _uiState.postValue(newState)
            _widgetUpdateEvent.postValue(Event(Unit))
        }
    }

    fun savePayPeriod(period: PayPeriod) = viewModelScope.launch { repository.savePayPeriod(period); loadData() }
    fun savePayday(day: Int) = viewModelScope.launch { repository.savePayday(day); loadData() }
    fun saveBiWeeklyReferenceDate(date: LocalDate) = viewModelScope.launch { repository.saveBiWeeklyReferenceDate(date); loadData() }
    fun saveSalary(salary: Long) = viewModelScope.launch { repository.saveSalary(salary); loadData() }
    fun saveMonthlySavings(amount: Long) = viewModelScope.launch { repository.saveMonthlySavings(amount); loadData() }

    fun addOrUpdateGoal(name: String, amount: Double, existingGoalId: String?) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val isFirstGoal = currentGoals.isEmpty() && existingGoalId == null
            if (existingGoalId == null) {
                currentGoals.add(SavingsGoal(name = name, targetAmount = amount))
            } else {
                val index = currentGoals.indexOfFirst { it.id == existingGoalId }
                if (index != -1) {
                    currentGoals[index] = currentGoals[index].copy(name = name, targetAmount = amount)
                }
            }
            repository.saveGoals(currentGoals)
            if (isFirstGoal) {
                val unlockedIds = repository.getUnlockedAchievementIds()
                if (!unlockedIds.contains("FIRST_GOAL")) {
                    repository.unlockAchievement("FIRST_GOAL")
                    AchievementsManager.getAllAchievements().find { it.id == "FIRST_GOAL" }?.let {
                        _newAchievementEvent.postValue(Event(it))
                    }
                }
            }
            updateUi(repository.getTotalExpenses().asLiveData().value ?: 0.0)
        }
    }

    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch {
        currentGoals.remove(goal)
        repository.saveGoals(currentGoals)
        updateUi(repository.getTotalExpenses().asLiveData().value ?: 0.0)
    }

    fun onSettingsResult() {
        loadData()
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(amount)
    }
}
