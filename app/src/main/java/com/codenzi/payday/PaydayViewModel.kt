package com.codenzi.payday

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieEntry
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    private val _dailySpendingData = MutableLiveData<Pair<List<BarEntry>, List<String>>>()
    val dailySpendingData: LiveData<Pair<List<BarEntry>, List<String>>> = _dailySpendingData

    private val _monthlyCategorySpendingData = MutableLiveData<Pair<List<Entry>, List<String>>>()
    val monthlyCategorySpendingData: LiveData<Pair<List<Entry>, List<String>>> = _monthlyCategorySpendingData

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
        checkUsageStreakAchievements()
        loadData()
    }

    fun deleteAccount() {
        viewModelScope.launch {
            repository.deleteAllUserData()
        }
    }

    private fun checkUsageStreakAchievements() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val firstLaunchDateStr = repository.getFirstLaunchDate().first()

            if (firstLaunchDateStr == null) {
                repository.setFirstLaunchDate(today)
                return@launch
            }

            val firstLaunchDate = LocalDate.parse(firstLaunchDateStr)
            val daysSinceFirstLaunch = ChronoUnit.DAYS.between(firstLaunchDate, today)

            val achievementsToCheck = mapOf(
                "STREAK_7_DAYS" to 7L,
                "STREAK_30_DAYS" to 30L,
                "STREAK_180_DAYS" to 180L,
                "LEGEND_ONE_YEAR" to 365L
            )

            achievementsToCheck.forEach { (id, requiredDays) ->
                if (daysSinceFirstLaunch >= requiredDays) {
                    unlockAchievement(id)
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
                @Suppress("UNCHECKED_CAST")
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

            val pieEntries = categorySpending
                .filter { it.categoryId != ExpenseCategory.SAVINGS.ordinal }
                .map { spending ->
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
            unlockAchievement("FIRST_TRANSACTION")
            if (isRecurring) {
                unlockAchievement("AUTO_PILOT")
            }
            checkCategoryExpertAchievement()
            loadData()
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
            if (isRecurring) {
                unlockAchievement("AUTO_PILOT")
            }
            checkCategoryExpertAchievement()
            loadData()
        }
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.deleteTransaction(transaction)
        loadData()
    }

    fun addOrUpdateGoal(name: String, amount: Double, existingGoalId: String?, targetDate: Long?, categoryId: Int) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val currentGoals = repository.getGoals().first().toMutableList()

            if (existingGoalId == null) {
                currentGoals.add(SavingsGoal(name = name, targetAmount = amount, savedAmount = 0.0, targetDate = targetDate, categoryId = categoryId))
                unlockAchievement("FIRST_GOAL")
                if (currentGoals.size >= 3) {
                    unlockAchievement("COLLECTOR")
                }
            } else {
                val index = currentGoals.indexOfFirst { it.id == existingGoalId }
                if (index != -1) {
                    val existingSavedAmount = currentGoals[index].savedAmount
                    currentGoals[index] = currentGoals[index].copy(name = name, targetAmount = amount, savedAmount = existingSavedAmount, targetDate = targetDate, categoryId = categoryId)
                }
            }
            repository.saveGoals(currentGoals)
            loadData()
        }
    }

    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch {
        val currentGoals = repository.getGoals().first().toMutableList()
        currentGoals.remove(goal)
        repository.saveGoals(currentGoals)
        loadData()
    }

    fun onSettingsResult() {
        checkThemeAchievement()
        loadData()
    }

    private suspend fun checkAndProcessNewCycle(result: PaydayResult) {
        val lastProcessedCycleEndDateStr = repository.getLastProcessedCycleEndDate().first()
        val currentCycleStartDate = result.cycleStartDate

        if (lastProcessedCycleEndDateStr == null || LocalDate.parse(lastProcessedCycleEndDateStr).isBefore(currentCycleStartDate)) {
            unlockAchievement("PAYDAY_HYPE")

            val previousCycleEndDate = currentCycleStartDate.minusDays(1).toDate()
            val previousCycleStartDate = PaydayCalculator.calculate(
                repository.getPayPeriod().first(),
                repository.getPaydayValue().first(),
                repository.getBiWeeklyRefDateString().first(),
                repository.isWeekendAdjustmentEnabled().first()
            )?.cycleStartDate?.toDate()

            if (previousCycleStartDate != null) {
                val salary = repository.getSalaryAmount().first()
                val expenses = repository.getTotalExpensesBetweenDates(previousCycleStartDate, previousCycleEndDate).first() ?: 0.0
                if (salary > expenses) {
                    unlockAchievement("BUDGET_WIZARD")
                    val consecutivePositiveCycles = (repository.getConsecutivePositiveCycles().first() ?: 0) + 1
                    repository.saveConsecutivePositiveCycles(consecutivePositiveCycles)
                    if (consecutivePositiveCycles >= 3) {
                        unlockAchievement("CYCLE_CHAMPION")
                    }
                } else {
                    repository.saveConsecutivePositiveCycles(0)
                }
            }

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

            if (repository.isAutoBackupEnabled().first()) {
                performAutoBackup()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun performAutoBackup() {
        viewModelScope.launch {
            if (GoogleSignIn.getLastSignedInAccount(getApplication()) == null) return@launch

            try {
                val googleDriveManager = GoogleDriveManager(getApplication())
                val backupData = repository.getAllDataForBackup()
                val backupJson = Gson().toJson(backupData)
                googleDriveManager.uploadFileContent(backupJson)
                Log.d("AutoBackup", "Otomatik yedekleme başarıyla tamamlandı.")
            } catch (e: Exception) {
                Log.e("AutoBackup", "Otomatik yedekleme sırasında bir hata oluştu.", e)
            }
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
            categoryId = ExpenseCategory.SAVINGS.ordinal,
            isRecurringTemplate = false
        )
        repository.insertTransaction(transaction)

        if (updatedGoal.savedAmount >= updatedGoal.targetAmount) {
            unlockAchievement("GOAL_COMPLETED")
        }

        val totalSavedAmount = currentGoals.sumOf { it.savedAmount }
        if (totalSavedAmount >= 1000) unlockAchievement("SAVER_LV1")
        if (totalSavedAmount >= 10000) unlockAchievement("SAVER_LV2")
        if (totalSavedAmount >= 50000) unlockAchievement("SAVER_LV3")

        loadData()
    }

    fun loadDailySpending(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            repository.getDailySpendingForChart(startDate, endDate).collect { dailySpendingList ->
                val labels = dailySpendingList.map { it.day.substring(5) }
                val barEntries = dailySpendingList.mapIndexed { index, dailySpending ->
                    BarEntry(index.toFloat(), dailySpending.totalAmount.toFloat())
                }
                _dailySpendingData.postValue(Pair(barEntries, labels))
            }
        }
    }

    fun loadMonthlySpendingForCategory(categoryId: Int) {
        viewModelScope.launch {
            repository.getMonthlySpendingForCategory(categoryId).collect { monthlySpendingList ->
                val labels = monthlySpendingList.map {
                    val dateParts = it.month.split("-")
                    if (dateParts.size == 2) "${dateParts[1]}/${dateParts[0]}" else it.month
                }
                val lineEntries = monthlySpendingList.mapIndexed { index, monthlySpending ->
                    Entry(index.toFloat(), monthlySpending.totalAmount.toFloat())
                }
                _monthlyCategorySpendingData.postValue(Pair(lineEntries, labels))
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(amount)
    }

    fun savePayPeriod(period: PayPeriod) = viewModelScope.launch { repository.savePayPeriod(period) }
    fun savePayday(day: Int) = viewModelScope.launch { repository.savePayday(day) }
    fun saveBiWeeklyReferenceDate(date: LocalDate) = viewModelScope.launch { repository.saveBiWeeklyReferenceDate(date) }
    fun saveSalary(salary: Long) = viewModelScope.launch { repository.saveSalary(salary) }
    fun saveMonthlySavings(amount: Long) = viewModelScope.launch { repository.saveMonthlySavings(amount) }

    private fun unlockAchievement(achievementId: String) {
        viewModelScope.launch {
            val unlockedIds = repository.getUnlockedAchievementIds().first()
            if (!unlockedIds.contains(achievementId)) {
                repository.unlockAchievement(achievementId)
                AchievementsManager.getAllAchievements().find { it.id == achievementId }?.let {
                    _newAchievementEvent.postValue(Event(it))
                }
            }
        }
    }

    private fun checkCategoryExpertAchievement() {
        viewModelScope.launch {
            val allTransactions = repository.getAllTransactionsForAchievements().first()
            val distinctCategories = allTransactions
                .filter { it.categoryId != ExpenseCategory.SAVINGS.ordinal }
                .map { it.categoryId }
                .distinct()

            if (distinctCategories.size >= 5) {
                unlockAchievement("CATEGORY_EXPERT")
            }
        }
    }

    private fun checkThemeAchievement() {
        viewModelScope.launch {
            val currentTheme = repository.getTheme().first()
            if (currentTheme == "Dark") {
                unlockAchievement("DARK_SIDE")
            }
        }
    }

    fun triggerSetupCompleteAchievement() {
        unlockAchievement("SETUP_COMPLETE")
    }

    fun triggerBackupHeroAchievement() {
        unlockAchievement("BACKUP_HERO")
    }

    fun triggerReportsViewedAchievement() {
        unlockAchievement("REPORTS_VIEWED")
    }
}
