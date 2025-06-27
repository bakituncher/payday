// Konum: app/src/main/java/com/example/payday/PaydayViewModel.kt

package com.example.payday

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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

    private val currentPayCycle = MutableStateFlow<Pair<Date, Date>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactionsForCurrentCycle: LiveData<List<Transaction>> = currentPayCycle.flatMapLatest { cycle ->
        if (cycle != null) {
            repository.getTransactionsBetweenDates(cycle.first, cycle.second)
        } else {
            flowOf(emptyList())
        }
    }.asLiveData()


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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getPayPeriod(),
                repository.getPaydayValue(),
                repository.getBiWeeklyRefDateString(),
                repository.isWeekendAdjustmentEnabled(),
                repository.getSalaryAmount(),
                repository.getMonthlySavingsAmount(),
                repository.getGoals()
            ) { values ->
                val payPeriod = values[0] as PayPeriod
                val paydayValue = values[1] as Int
                val biWeeklyRefDate = values[2] as? String
                val weekendAdjustment = values[3] as Boolean
                val salaryAmount = values[4] as Long
                val monthlySavings = values[5] as Long
                val goals = values[6] as MutableList<SavingsGoal>

                val result = PaydayCalculator.calculate(payPeriod, paydayValue, biWeeklyRefDate, weekendAdjustment)

                if (result != null) {
                    currentPayCycle.value = Pair(result.cycleStartDate.toDate(), result.cycleEndDate.toDate())

                    checkAndProcessNewCycle(result)

                    val totalExpensesFlow = repository.getTotalExpensesBetweenDates(result.cycleStartDate.toDate(), result.cycleEndDate.toDate())
                    val categorySpendingFlow = repository.getSpendingByCategoryBetweenDates(result.cycleStartDate.toDate(), result.cycleEndDate.toDate())

                    combine(totalExpensesFlow, categorySpendingFlow) { totalExpenses, categorySpending ->
                        updateUi(result, salaryAmount, monthlySavings, totalExpenses ?: 0.0, categorySpending, goals)
                    }.collect()
                } else {
                    _uiState.postValue(PaydayUiState(daysLeftText = context.getString(R.string.day_not_set_placeholder), daysLeftSuffix = context.getString(R.string.welcome_message)))
                }
            }.collect()
        }
    }

    private fun updateUi(
        paydayResult: PaydayResult,
        salaryAmount: Long,
        monthlySavingsAmount: Long,
        totalExpenses: Double,
        categorySpending: List<CategorySpending>,
        goals: List<SavingsGoal>
    ) {
        viewModelScope.launch {
            val remainingAmount = salaryAmount.toDouble() - totalExpenses

            val pieEntries = categorySpending.map { spending ->
                val category = ExpenseCategory.fromId(spending.categoryId)
                PieEntry(spending.totalAmount.toFloat(), category.categoryName)
            }

            val transactionsList = transactionsForCurrentCycle.value ?: emptyList()
            val newState = PaydayUiState(
                daysLeftText = if (paydayResult.isPayday) "🎉" else paydayResult.daysLeft.toString(),
                daysLeftSuffix = if (paydayResult.isPayday) context.getString(R.string.payday_is_today) else context.getString(R.string.days_left_suffix),
                isPayday = paydayResult.isPayday,
                savingsGoals = goals,
                areGoalsVisible = goals.isNotEmpty(),
                areTransactionsVisible = transactionsList.isNotEmpty(),
                incomeText = formatCurrency(salaryAmount.toDouble()),
                expensesText = "- ${formatCurrency(totalExpenses)}",
                remainingText = formatCurrency(remainingAmount),
                actualRemainingAmountForGoals = remainingAmount,
                categorySpendingData = pieEntries
            )
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

    fun onSettingsResult() {
        loadData()
    }

    private suspend fun checkAndProcessNewCycle(result: PaydayResult) {
        val lastProcessedCycleEndDateStr = repository.getLastProcessedCycleEndDate().first()
        val currentCycleStartDate = result.cycleStartDate

        if (lastProcessedCycleEndDateStr == null || LocalDate.parse(lastProcessedCycleEndDateStr).isBefore(currentCycleStartDate)) {
            val templates = repository.getRecurringTransactionTemplates().first()
            templates.forEach { template ->
                val newTransaction = Transaction(
                    name = template.name,
                    amount = template.amount,
                    date = Date(),
                    categoryId = template.categoryId,
                    isRecurringTemplate = false
                )
                repository.insertTransaction(newTransaction)
            }
            repository.saveLastProcessedCycleEndDate(result.cycleEndDate)
        }
    }

    fun addFundsToGoal(goalId: String, amountToAdd: Double) = viewModelScope.launch {
        val currentState = _uiState.value ?: return@launch
        val currentGoals = repository.getGoals().first().toMutableList()

        val goalIndex = currentGoals.indexOfFirst { it.id == goalId }
        if (goalIndex == -1) return@launch

        val goal = currentGoals[goalIndex]

        if (amountToAdd > currentState.actualRemainingAmountForGoals) {
            return@launch
        }

        val neededAmount = goal.targetAmount - goal.savedAmount
        val finalAmountToAdd = minOf(amountToAdd, neededAmount)

        if (finalAmountToAdd <= 0) return@launch

        val updatedGoal = goal.copy(savedAmount = goal.savedAmount + finalAmountToAdd)
        currentGoals[goalIndex] = updatedGoal

        repository.saveGoals(currentGoals)

        val transaction = Transaction(
            name = "'${goal.name}' hedefine aktarıldı",
            amount = finalAmountToAdd,
            date = Date(),
            categoryId = ExpenseCategory.OTHER.ordinal,
            isRecurringTemplate = false
        )
        repository.insertTransaction(transaction)
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(amount)
    }

    fun savePayPeriod(period: PayPeriod) = viewModelScope.launch { repository.savePayPeriod(period) }
    fun savePayday(day: Int) = viewModelScope.launch { repository.savePayday(day) }
    fun saveBiWeeklyReferenceDate(date: LocalDate) = viewModelScope.launch { repository.saveBiWeeklyReferenceDate(date) }
    fun saveSalary(salary: Long) = viewModelScope.launch { repository.saveSalary(salary) }
    fun saveMonthlySavings(amount: Long) = viewModelScope.launch { repository.saveMonthlySavings(amount) }
}