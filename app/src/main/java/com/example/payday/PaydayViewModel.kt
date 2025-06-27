package com.example.payday

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@SuppressLint("StaticFieldLeak")
class PaydayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaydayRepository(application)
    private val context = application.applicationContext

    private val _uiState = MutableLiveData<PaydayUiState>()
    val uiState: LiveData<PaydayUiState> = _uiState

    private val _widgetUpdateEvent = MutableLiveData<Event<Unit>>()
    val widgetUpdateEvent: LiveData<Event<Unit>> = _widgetUpdateEvent

    private val _newAchievementEvent = MutableLiveData<Event<Achievement>>()
    val newAchievementEvent: LiveData<Event<Achievement>> = _newAchievementEvent

    val allTransactions: LiveData<List<Transaction>> = repository.getAllTransactions().asLiveData()

    init {
        checkWeeklyAchievement()
        loadData()
    }

    private fun checkWeeklyAchievement() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val firstLaunchDateStr = repository.getFirstLaunchDate().first()
            if (firstLaunchDateStr == null) {
                repository.setFirstLaunchDate(today)
            } else {
                val firstLaunchDate = LocalDate.parse(firstLaunchDateStr)
                val daysBetween = ChronoUnit.DAYS.between(firstLaunchDate, today)
                if (daysBetween >= 7) {
                    val unlockedIds = repository.getUnlockedAchievementIds().first()
                    if (!unlockedIds.contains("FIRST_WEEK")) {
                        repository.unlockAchievement("FIRST_WEEK")
                        AchievementsManager.getAllAchievements().find { it.id == "FIRST_WEEK" }?.let {
                            _newAchievementEvent.postValue(Event(it))
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getTotalExpenses(),
                repository.getSpendingByCategory(),
                repository.getPayPeriod(),
                repository.getPaydayValue(),
                repository.getBiWeeklyRefDateString(),
                repository.getSalaryAmount(),
                repository.isWeekendAdjustmentEnabled(),
                repository.getMonthlySavingsAmount(),
                repository.getGoals()
            ) { values ->
                val totalExpenses = values[0] as? Double ?: 0.0
                val categorySpending = values[1] as? List<CategorySpending> ?: emptyList()
                val payPeriod = values[2] as PayPeriod
                val paydayValue = values[3] as Int
                val biWeeklyRefDate = values[4] as? String
                val salaryAmount = values[5] as Long
                val weekendAdjustment = values[6] as Boolean
                val monthlySavings = values[7] as Long
                val goals = values[8] as? MutableList<SavingsGoal> ?: mutableListOf()

                updateUi(totalExpenses, categorySpending, payPeriod, paydayValue, biWeeklyRefDate, salaryAmount, weekendAdjustment, monthlySavings, goals)

            }.collectLatest { }
        }
    }

    private fun updateUi(totalExpenses: Double, categorySpending: List<CategorySpending>, payPeriod: PayPeriod, paydayValue: Int, biWeeklyRefDateString: String?, salaryAmount: Long, weekendAdjustmentEnabled: Boolean, monthlySavingsAmount: Long, goals: List<SavingsGoal>) {
        viewModelScope.launch {
            val result = PaydayCalculator.calculate(payPeriod, paydayValue, biWeeklyRefDateString, salaryAmount, weekendAdjustmentEnabled)

            val remainingAmount = salaryAmount.toDouble() - totalExpenses

            val theoreticalSavings = if (result != null) {
                val daysPassed = result.totalDaysInCycle - result.daysLeft
                val dailySavingsRate = if (result.totalDaysInCycle > 0) monthlySavingsAmount.toDouble() / result.totalDaysInCycle else 0.0
                dailySavingsRate * daysPassed.coerceAtLeast(0)
            } else { 0.0 }

            result?.let {
                val unlockedIds = repository.getUnlockedAchievementIds().first()
                if (it.isPayday && !unlockedIds.contains("PAYDAY_HYPE")) {
                    repository.unlockAchievement("PAYDAY_HYPE")
                    AchievementsManager.getAllAchievements().find { ach -> ach.id == "PAYDAY_HYPE" }?.let { ach ->
                        _newAchievementEvent.postValue(Event(ach))
                    }
                }
                if (theoreticalSavings >= 1000 && !unlockedIds.contains("SAVER_LV1")) {
                    repository.unlockAchievement("SAVER_LV1")
                    AchievementsManager.getAllAchievements().find { ach -> ach.id == "SAVER_LV1" }?.let { ach ->
                        _newAchievementEvent.postValue(Event(ach))
                    }
                }
            }

            val pieEntries = categorySpending.map { spending ->
                val category = ExpenseCategory.fromId(spending.categoryId)
                PieEntry(spending.totalAmount.toFloat(), category.categoryName)
            }

            val transactionsList = allTransactions.value ?: emptyList()
            val newState = if (result == null) {
                PaydayUiState(daysLeftText = context.getString(R.string.day_not_set_placeholder), daysLeftSuffix = context.getString(R.string.welcome_message))
            } else {
                PaydayUiState(
                    daysLeftText = if (result.isPayday) "🎉" else result.daysLeft.toString(),
                    daysLeftSuffix = if (result.isPayday) context.getString(R.string.payday_is_today) else context.getString(R.string.days_left_suffix),
                    isPayday = result.isPayday,
                    savingsGoals = goals,
                    areGoalsVisible = goals.isNotEmpty(),
                    areTransactionsVisible = transactionsList.isNotEmpty(),
                    incomeText = formatCurrency(salaryAmount.toDouble()),
                    expensesText = "- ${formatCurrency(totalExpenses)}",
                    remainingText = formatCurrency(remainingAmount),
                    actualRemainingAmountForGoals = remainingAmount,
                    categorySpendingData = pieEntries
                )
            }
            _uiState.postValue(newState)
            _widgetUpdateEvent.postValue(Event(Unit))
        }
    }

    fun insertTransaction(name: String, amount: Double, categoryId: Int, isRecurring: Boolean) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val transaction = Transaction(
                name = name,
                amount = amount,
                date = Date(),
                categoryId = categoryId,
                isRecurringTemplate = isRecurring
            )
            repository.insertTransaction(transaction)
        }
    }

    fun updateTransaction(id: Int, newName: String, newAmount: Double, newCategoryId: Int, isRecurring: Boolean) = viewModelScope.launch {
        if (newName.isNotBlank() && newAmount > 0) {
            val updatedTransaction = Transaction(
                id = id,
                name = newName,
                amount = newAmount,
                date = Date(),
                categoryId = newCategoryId,
                isRecurringTemplate = isRecurring
            )
            repository.updateTransaction(updatedTransaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.deleteTransaction(transaction)
    }

    fun addOrUpdateGoal(name: String, amount: Double, existingGoalId: String?, targetDate: Long?) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val currentGoals = repository.getGoals().first().toMutableList()
            val isFirstGoal = currentGoals.isEmpty() && existingGoalId == null

            if (existingGoalId == null) {
                currentGoals.add(SavingsGoal(name = name, targetAmount = amount, targetDate = targetDate))
            } else {
                val index = currentGoals.indexOfFirst { it.id == existingGoalId }
                if (index != -1) {
                    currentGoals[index] = currentGoals[index].copy(name = name, targetAmount = amount, targetDate = targetDate)
                }
            }
            repository.saveGoals(currentGoals)

            if (isFirstGoal) {
                val unlockedIds = repository.getUnlockedAchievementIds().first()
                if (!unlockedIds.contains("FIRST_GOAL")) {
                    repository.unlockAchievement("FIRST_GOAL")
                    AchievementsManager.getAllAchievements().find { it.id == "FIRST_GOAL" }?.let {
                        _newAchievementEvent.postValue(Event(it))
                    }
                }
            }
        }
    }

    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch {
        val currentGoals = repository.getGoals().first().toMutableList()
        currentGoals.remove(goal)
        repository.saveGoals(currentGoals)
    }

    fun onSettingsResult() {}

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(amount)
    }

    fun savePayPeriod(period: PayPeriod) = viewModelScope.launch { repository.savePayPeriod(period) }
    fun savePayday(day: Int) = viewModelScope.launch { repository.savePayday(day) }
    fun saveBiWeeklyReferenceDate(date: LocalDate) = viewModelScope.launch { repository.saveBiWeeklyReferenceDate(date) }
    fun saveSalary(salary: Long) = viewModelScope.launch { repository.saveSalary(salary) }
    fun saveMonthlySavings(amount: Long) = viewModelScope.launch { repository.saveMonthlySavings(amount) }
}