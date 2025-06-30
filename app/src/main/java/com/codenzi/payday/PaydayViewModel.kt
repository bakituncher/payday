// Konum: app/src/main/java/com/codenzi/payday/PaydayViewModel.kt

package com.codenzi.payday

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieEntry
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
        // ViewModel başlatıldığında, kullanım süresine bağlı başarımları kontrol et
        checkUsageStreakAchievements()
        loadData()
    }

    /**
     * Kullanım süresine dayalı başarımları kontrol eder (7 gün, 30 gün, 180 gün, 1 yıl).
     * Uygulama her açıldığında çağrılır.
     */
    private fun checkUsageStreakAchievements() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val firstLaunchDateStr = repository.getFirstLaunchDate().first()

            if (firstLaunchDateStr == null) {
                // Bu, uygulamanın ilk açılışı. Tarihi kaydet.
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

    /**
     * Yeni bir harcama ekler ve ilgili başarımları kontrol eder.
     * - "Harcama Günlüğü" (FIRST_TRANSACTION)
     * - "Kategori Uzmanı" (CATEGORY_EXPERT)
     * - "Otomatik Pilot" (AUTO_PILOT)
     */
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

            // Başarım kontrolleri
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

            // Başarım kontrolleri
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

    /**
     * Yeni bir tasarruf hedefi ekler/günceller ve ilgili başarımları kontrol eder.
     * - "İlk Adım" (FIRST_GOAL)
     * - "Koleksiyoncu" (COLLECTOR)
     */
    fun addOrUpdateGoal(name: String, amount: Double, existingGoalId: String?, targetDate: Long?, categoryId: Int) = viewModelScope.launch {
        if (name.isNotBlank() && amount > 0) {
            val currentGoals = repository.getGoals().first().toMutableList()

            if (existingGoalId == null) { // Yeni hedef ekleniyor
                currentGoals.add(SavingsGoal(name = name, targetAmount = amount, savedAmount = 0.0, targetDate = targetDate, categoryId = categoryId))
                unlockAchievement("FIRST_GOAL")
                if (currentGoals.size >= 3) {
                    unlockAchievement("COLLECTOR")
                }
            } else { // Mevcut hedef güncelleniyor
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
        // Ayarlar ekranından dönüldüğünde temayla ilgili başarımı kontrol et
        checkThemeAchievement()
        loadData()
    }

    /**
     * Yeni bir ödeme döngüsü başladığında tetiklenir ve ilgili başarımları kontrol eder.
     * - "Maaş Günü!" (PAYDAY_HYPE)
     * - "Bütçe Sihirbazı" (BUDGET_WIZARD)
     * - "Döngü Şampiyonu" (CYCLE_CHAMPION)
     */
    private suspend fun checkAndProcessNewCycle(result: PaydayResult) {
        val lastProcessedCycleEndDateStr = repository.getLastProcessedCycleEndDate().first()
        val currentCycleStartDate = result.cycleStartDate

        if (lastProcessedCycleEndDateStr == null || LocalDate.parse(lastProcessedCycleEndDateStr).isBefore(currentCycleStartDate)) {
            // ----- YENİ DÖNGÜ BAŞLADI -----

            unlockAchievement("PAYDAY_HYPE")

            // Bir önceki döngü pozitif bakiye ile mi bitti?
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
                    // Önceki döngü pozitifte bitti
                    unlockAchievement("BUDGET_WIZARD")

                    val consecutivePositiveCycles = (repository.getConsecutivePositiveCycles().first() ?: 0) + 1
                    repository.saveConsecutivePositiveCycles(consecutivePositiveCycles)
                    if (consecutivePositiveCycles >= 3) {
                        unlockAchievement("CYCLE_CHAMPION")
                    }
                } else {
                    // Pozitif seri bozuldu
                    repository.saveConsecutivePositiveCycles(0)
                }
            }

            // Tekrarlayan harcamaları oluştur
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

    /**
     * Bir hedefe para eklendiğinde çağrılır ve ilgili başarımları kontrol eder.
     * - "Kumbaracı" (SAVER_LV1)
     * - "Yatırımcı" (SAVER_LV2)
     * - "Tasarruf Gurusu" (SAVER_LV3)
     * - "Hedef Avcısı" (GOAL_COMPLETED)
     */
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

        // Başarım kontrolleri
        if (updatedGoal.savedAmount >= updatedGoal.targetAmount) {
            unlockAchievement("GOAL_COMPLETED")
        }

        val totalSavedAmount = currentGoals.sumOf { it.savedAmount }
        if (totalSavedAmount >= 1000) unlockAchievement("SAVER_LV1")
        if (totalSavedAmount >= 10000) unlockAchievement("SAVER_LV2")
        if (totalSavedAmount >= 50000) unlockAchievement("SAVER_LV3")


        loadData()
    }

    // --- Raporlama İçin Fonksiyonlar ---
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

    // --- Onboarding ve Ayarlar için Kaydetme Fonksiyonları ---
    fun savePayPeriod(period: PayPeriod) = viewModelScope.launch { repository.savePayPeriod(period) }
    fun savePayday(day: Int) = viewModelScope.launch { repository.savePayday(day) }
    fun saveBiWeeklyReferenceDate(date: LocalDate) = viewModelScope.launch { repository.saveBiWeeklyReferenceDate(date) }
    fun saveSalary(salary: Long) = viewModelScope.launch { repository.saveSalary(salary) }
    fun saveMonthlySavings(amount: Long) = viewModelScope.launch { repository.saveMonthlySavings(amount) }

    // ===================================================================
    //                        BAŞARIM YARDIMCI METOTLARI
    // ===================================================================

    /**
     * Verilen ID'ye sahip başarımın kilidini açar. Eğer başarım zaten açıksa hiçbir şey yapmaz.
     * @param achievementId Kilidi açılacak başarımın `AchievementsManager`'daki ID'si.
     */
    private fun unlockAchievement(achievementId: String) {
        viewModelScope.launch {
            val unlockedIds = repository.getUnlockedAchievementIds().first()
            if (!unlockedIds.contains(achievementId)) {
                repository.unlockAchievement(achievementId)
                // UI'da Snackbar göstermek için event tetikle
                AchievementsManager.getAllAchievements().find { it.id == achievementId }?.let {
                    _newAchievementEvent.postValue(Event(it))
                }
            }
        }
    }

    /**
     * "Kategori Uzmanı" başarımını kontrol eder.
     * Kullanıcı 5 farklı harcama kategorisi kullandıysa başarımı açar.
     */
    private fun checkCategoryExpertAchievement() {
        viewModelScope.launch {
            val allTransactions = repository.getAllTransactionsForAchievements().first()
            val distinctCategories = allTransactions
                .filter { it.categoryId != ExpenseCategory.SAVINGS.ordinal } // Tasarruf kategorisi sayılmaz
                .map { it.categoryId }
                .distinct()

            if (distinctCategories.size >= 5) {
                unlockAchievement("CATEGORY_EXPERT")
            }
        }
    }

    /**
     * "Kara Kutu" (Koyu Tema) başarımını kontrol eder.
     */
    private fun checkThemeAchievement() {
        viewModelScope.launch {
            val currentTheme = repository.getTheme().first()
            if (currentTheme == "Dark") {
                unlockAchievement("DARK_SIDE")
            }
        }
    }

    /**
     * Diğer activity'lerden (örn. MainActivity, OnboardingActivity) çağrılacak public bir metot.
     * Bu, ViewModel'in iç mantığını dışarıya açmadan başarım kilidi açmayı sağlar.
     */
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