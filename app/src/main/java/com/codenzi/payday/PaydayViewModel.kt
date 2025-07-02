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
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.min

fun LocalDate.toStartOfDayDate(): Date {
    return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}

fun LocalDate.toEndOfDayDate(): Date {
    return Date.from(this.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant())
}


@SuppressLint("StaticFieldLeak")
@Suppress("DEPRECATION")
class PaydayViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "PaydayAutomation"
    private val repository = PaydayRepository(application)
    private val context = application.applicationContext

    private val _uiState = MutableLiveData<PaydayUiState>()
    val uiState: LiveData<PaydayUiState> = _uiState

    private val _financialInsight = MutableLiveData<Event<String?>>()
    val financialInsight: LiveData<Event<String?>> = _financialInsight

    private val _widgetUpdateEvent = MutableLiveData<Event<Unit>>()
    val widgetUpdateEvent: LiveData<Event<Unit>> = _widgetUpdateEvent

    private val _newAchievementEvent = MutableLiveData<Event<Achievement>>()
    val newAchievementEvent: LiveData<Event<Achievement>> = _newAchievementEvent

    private val _goalCompletedEvent = MutableLiveData<Event<SavingsGoal>>()
    val goalCompletedEvent: LiveData<Event<SavingsGoal>> = _goalCompletedEvent

    private val _dailySpendingData = MutableLiveData<Pair<List<BarEntry>, List<String>>>()
    val dailySpendingData: LiveData<Pair<List<BarEntry>, List<String>>> = _dailySpendingData

    private val _monthlyCategorySpendingData = MutableLiveData<Pair<List<Entry>, List<String>>>()
    val monthlyCategorySpendingData: LiveData<Pair<List<Entry>, List<String>>> = _monthlyCategorySpendingData

    private val _showRestoreWarningEvent = MutableLiveData<Event<Unit>>()
    val showRestoreWarningEvent: LiveData<Event<Unit>> = _showRestoreWarningEvent

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private val _accountDeletionResult = MutableLiveData<Event<Boolean>>()
    val accountDeletionResult: LiveData<Event<Boolean>> = _accountDeletionResult


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
        checkForRestoreValidation()
    }

    private fun checkForRestoreValidation() {
        viewModelScope.launch {
            if (repository.isRestoreValidationNeeded().first()) {
                _showRestoreWarningEvent.postValue(Event(Unit))
                repository.clearRestoreValidationFlag()
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val success = repository.deleteAllUserData()
            _accountDeletionResult.postValue(Event(success))
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            repository.clearLocalData()
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
                repository.getGoals(),
                repository.getCarryOverAmount()
            ) { values ->
                val payPeriod = values[0] as PayPeriod
                val paydayValue = values[1] as Int
                val biWeeklyRefDate = values[2] as? String
                val weekendAdjustment = values[3] as Boolean
                val salaryAmount = values[4] as Long
                @Suppress("UNCHECKED_CAST")
                val goals = values[5] as MutableList<SavingsGoal>
                val carryOverAmount = values[6] as Long

                val result = PaydayCalculator.calculate(LocalDate.now(), payPeriod, paydayValue, biWeeklyRefDate, weekendAdjustment)

                if (result != null) {
                    val cycleStartDate = result.cycleStartDate.toStartOfDayDate()
                    val cycleEndDate = result.cycleEndDate.toEndOfDayDate()

                    currentPayCycle.value = Pair(cycleStartDate, cycleEndDate)

                    checkAndProcessPostPaydayTasks()

                    val totalExpensesFlow = repository.getTotalExpensesBetweenDates(cycleStartDate, cycleEndDate)
                    val totalSavingsFlow = repository.getTotalSavingsBetweenDates(cycleStartDate, cycleEndDate)
                    val categorySpendingFlow = repository.getSpendingByCategoryBetweenDates(cycleStartDate, cycleEndDate)

                    combine(totalExpensesFlow, totalSavingsFlow, categorySpendingFlow) { totalExpenses, totalSavings, categorySpending ->
                        updateUi(result, salaryAmount, totalExpenses ?: 0.0, totalSavings ?: 0.0, goals, categorySpending, carryOverAmount)
                        generateFinancialInsights(totalExpenses ?: 0.0, categorySpending)
                    }.collect()
                } else {
                    _uiState.postValue(PaydayUiState(daysLeftText = context.getString(R.string.day_not_set_placeholder), daysLeftSuffix = context.getString(R.string.welcome_message)))
                }
            }.collect()
        }
    }

    private fun checkAndProcessPostPaydayTasks() {
        viewModelScope.launch {
            val yesterday = LocalDate.now().minusDays(1)

            val payPeriod = repository.getPayPeriod().first()
            val paydayValue = repository.getPaydayValue().first()
            val biWeeklyRefDate = repository.getBiWeeklyRefDateString().first()
            val weekendAdjustment = repository.isWeekendAdjustmentEnabled().first()

            val resultForYesterday = PaydayCalculator.calculate(yesterday, payPeriod, paydayValue, biWeeklyRefDate, weekendAdjustment)

            if (resultForYesterday != null && resultForYesterday.isPayday) {
                val lastProcessedDateStr = repository.getLastProcessedPayday().first()
                if (lastProcessedDateStr == null || !LocalDate.parse(lastProcessedDateStr).isEqual(yesterday)) {
                    runCycleEndTasks(resultForYesterday)
                }
            }
        }
    }

    private suspend fun runCycleEndTasks(paydayResult: PaydayResult) {
        unlockAchievement("PAYDAY_HYPE")

        val previousCycleStartDate = paydayResult.cycleStartDate.minusDays(paydayResult.totalDaysInCycle).toStartOfDayDate()
        val previousCycleEndDate = paydayResult.cycleStartDate.minusDays(1).toEndOfDayDate()
        val salary = repository.getSalaryAmount().first()
        val expenses = repository.getTotalExpensesBetweenDates(previousCycleStartDate, previousCycleEndDate).first() ?: 0.0
        val totalSavings = repository.getTotalSavingsBetweenDates(previousCycleStartDate, previousCycleEndDate).first() ?: 0.0
        val carryOver = repository.getCarryOverAmount().first()

        val remainingFromPreviousCycle = (salary + carryOver) - expenses - totalSavings
        if (remainingFromPreviousCycle > 0) {
            repository.saveCarryOverAmount(remainingFromPreviousCycle.toLong())
        } else {
            repository.saveCarryOverAmount(0L)
        }


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

        if (repository.isAutoSavingEnabled().first()) {
            val monthlySavings = repository.getMonthlySavingsAmount().first()
            if (monthlySavings > 0) {
                distributeSavingsToGoals(monthlySavings.toDouble())
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

        if (repository.isAutoBackupEnabled().first()) {
            performAutoBackup()
        }

        repository.saveLastProcessedPayday(LocalDate.now().minusDays(1))
    }

    private fun generateFinancialInsights(totalExpenses: Double, categorySpending: List<CategorySpending>) {
        if (categorySpending.isEmpty()) {
            _financialInsight.postValue(Event(null))
            return
        }

        val topCategorySpending = categorySpending.maxByOrNull { it.totalAmount }

        if (topCategorySpending != null && topCategorySpending.totalAmount > totalExpenses * 0.4) {
            val topCategory = ExpenseCategory.fromId(topCategorySpending.categoryId)
            val insight = context.getString(R.string.suggestion_high_spending, topCategory.categoryName)
            _financialInsight.postValue(Event(insight))
            return
        }

        val cycle = currentPayCycle.value ?: return
        val today = LocalDate.now()
        val cycleStart = cycle.first.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val cycleEnd = cycle.second.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val totalDaysInCycle = ChronoUnit.DAYS.between(cycleStart, cycleEnd)

        if (totalDaysInCycle > 0) {
            val daysPassed = ChronoUnit.DAYS.between(cycleStart, today)
            val cycleProgress = daysPassed.toDouble() / totalDaysInCycle.toDouble()
            val salary = uiState.value?.incomeText?.replace(Regex("[^\\d]"), "")?.toLongOrNull() ?: 0L
            if (salary > 0) {
                val spendingProgress = totalExpenses / salary
                if (cycleProgress > 0.5 && spendingProgress < 0.3) {
                    _financialInsight.postValue(Event(context.getString(R.string.suggestion_good_progress)))
                    return
                }
            }
        }

        _financialInsight.postValue(Event(null))
    }

    private fun updateUi(
        paydayResult: PaydayResult,
        salaryAmount: Long,
        totalExpenses: Double,
        totalSavings: Double,
        goals: List<SavingsGoal>,
        categorySpending: List<CategorySpending>,
        carryOverAmount: Long
    ) {
        val totalIncome = salaryAmount + carryOverAmount
        val remainingAmount = totalIncome - totalExpenses - totalSavings

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
            expensesText = formatCurrency(totalExpenses, "- "),
            savingsText = formatCurrency(totalSavings),
            remainingText = formatCurrency(remainingAmount),
            carryOverAmount = carryOverAmount,
            actualRemainingAmountForGoals = remainingAmount,
            categorySpendingData = pieEntries
        )
        _uiState.postValue(newState)
        _widgetUpdateEvent.postValue(Event(Unit))
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

    fun updateTransaction(transactionToUpdate: Transaction) = viewModelScope.launch {
        if (transactionToUpdate.name.isNotBlank() && transactionToUpdate.amount > 0) {
            repository.updateTransaction(transactionToUpdate)
            if (transactionToUpdate.isRecurringTemplate) {
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

    fun addOrUpdateGoal(name: String, amount: Double, existingGoalId: String?, targetDate: Long?, categoryId: Int, portion: Int) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val oldGoals = repository.getGoals().first()
            val newGoals = oldGoals.toMutableList()

            if (existingGoalId == null) {
                newGoals.add(SavingsGoal(name = name, targetAmount = amount, categoryId = categoryId, targetDate = targetDate, portion = portion))
                unlockAchievement("FIRST_GOAL")
                if (newGoals.size >= 3) {
                    unlockAchievement("COLLECTOR")
                }
            } else {
                val index = newGoals.indexOfFirst { it.id == existingGoalId }
                if (index != -1) {
                    newGoals[index] = newGoals[index].copy(name = name, targetAmount = amount, categoryId = categoryId, targetDate = targetDate, portion = portion)
                }
            }
            repository.saveGoals(newGoals)
            checkAndNotifyForCompletedGoals(oldGoals, newGoals)
            loadData()
        }
    }

    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch {
        val currentGoals = repository.getGoals().first().toMutableList()
        currentGoals.remove(goal)
        repository.saveGoals(currentGoals)
        loadData()
    }

    fun releaseFundsFromGoal(goal: SavingsGoal) = viewModelScope.launch {
        val refundTransaction = Transaction(
            name = context.getString(R.string.goal_refund_transaction_name, goal.name),
            amount = -goal.savedAmount,
            date = Date(),
            categoryId = ExpenseCategory.getSavingsCategoryId(),
            isRecurringTemplate = false
        )
        repository.insertTransaction(refundTransaction)
        deleteGoal(goal)
    }

    fun onSettingsResult() {
        checkThemeAchievement()
        loadData()
    }

    private suspend fun distributeSavingsToGoals(totalSavingsAmount: Double) {
        val oldGoals = repository.getGoals().first()
        if (oldGoals.none { !it.isComplete }) return

        val activeGoals = oldGoals.filter { !it.isComplete }
        val totalPortion = activeGoals.sumOf { it.portion }
        if (totalPortion <= 0) return

        val newGoals = oldGoals.toMutableList()

        activeGoals.forEach { goal ->
            val goalIndex = newGoals.indexOfFirst { it.id == goal.id }
            if (goalIndex != -1) {
                val currentGoal = newGoals[goalIndex]
                val goalShare = (currentGoal.portion.toDouble() / totalPortion) * totalSavingsAmount
                val neededForGoal = currentGoal.targetAmount - currentGoal.savedAmount
                val amountToAdd = min(goalShare, neededForGoal)

                if (amountToAdd > 0) {
                    newGoals[goalIndex] = currentGoal.copy(savedAmount = currentGoal.savedAmount + amountToAdd)
                    val transaction = Transaction(
                        name = context.getString(R.string.auto_savings_transaction_name, goal.name),
                        amount = amountToAdd,
                        date = Date(),
                        categoryId = ExpenseCategory.SAVINGS.ordinal
                    )
                    repository.insertTransaction(transaction)
                }
            }
        }
        repository.saveGoals(newGoals)
        checkAndNotifyForCompletedGoals(oldGoals, newGoals)
        loadData()
    }


    private fun performAutoBackup() {
        viewModelScope.launch {
            if (GoogleSignIn.getLastSignedInAccount(getApplication()) == null) return@launch

            try {
                val googleDriveManager = GoogleDriveManager(getApplication())
                val backupData = repository.getAllDataForBackup()
                val backupJson = Gson().toJson(backupData)
                googleDriveManager.uploadFileContent(backupJson)
            } catch (e: Exception) {
                Log.e("AutoBackup", "Otomatik yedekleme sırasında bir hata oluştu.", e)
            }
        }
    }

    fun addFundsToGoal(goalId: String, amountToAdd: Double) = viewModelScope.launch {
        val currentState = _uiState.value ?: return@launch
        if (amountToAdd > currentState.actualRemainingAmountForGoals) {
            _toastEvent.postValue(Event(context.getString(R.string.insufficient_funds_error)))
            return@launch
        }

        val oldGoals = repository.getGoals().first()
        val newGoals = oldGoals.toMutableList()
        val goalIndex = newGoals.indexOfFirst { it.id == goalId }

        if (goalIndex == -1 || newGoals[goalIndex].isComplete) return@launch

        val goal = newGoals[goalIndex]
        val neededAmount = goal.targetAmount - goal.savedAmount
        val finalAmountToAdd = min(amountToAdd, neededAmount)

        if (finalAmountToAdd <= 0) return@launch

        newGoals[goalIndex] = goal.copy(savedAmount = goal.savedAmount + finalAmountToAdd)
        repository.saveGoals(newGoals)

        val transaction = Transaction(
            name = context.getString(R.string.add_funds_to_goal_transaction_name, goal.name),
            amount = finalAmountToAdd,
            date = Date(),
            categoryId = ExpenseCategory.SAVINGS.ordinal
        )
        repository.insertTransaction(transaction)

        checkAndNotifyForCompletedGoals(oldGoals, newGoals)

        val totalSavedAmount = newGoals.sumOf { it.savedAmount }
        if (totalSavedAmount >= 1000) unlockAchievement("SAVER_LV1")
        if (totalSavedAmount >= 10000) unlockAchievement("SAVER_LV2")
        if (totalSavedAmount >= 50000) unlockAchievement("SAVER_LV3")

        loadData()
    }

    private fun checkAndNotifyForCompletedGoals(oldGoals: List<SavingsGoal>, newGoals: List<SavingsGoal>) {
        newGoals.forEach { newGoal ->
            val oldGoal = oldGoals.find { it.id == newGoal.id }
            if (newGoal.isComplete && (oldGoal == null || !oldGoal.isComplete)) {
                _goalCompletedEvent.postValue(Event(newGoal))
                unlockAchievement("GOAL_COMPLETED")
            }
        }
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

    private fun formatCurrency(amount: Double, prefix: String = ""): String {
        return prefix + NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(amount)
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
                .filter { it.categoryId != ExpenseCategory.getSavingsCategoryId() }
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