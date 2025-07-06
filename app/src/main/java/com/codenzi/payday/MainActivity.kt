package com.codenzi.payday

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.databinding.ActivityMainBinding
import com.codenzi.payday.notifications.NotificationScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var savingsGoalAdapter: SavingsGoalAdapter
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var repository: PaydayRepository
    private val gson = Gson()
    private val TAG = "PaydayBackup"
    private lateinit var googleDriveManager: GoogleDriveManager
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val titleHandler = Handler(Looper.getMainLooper())
    private lateinit var titleRunnable: Runnable
    private var isShowingGreeting = true
    private var greetingMessage = ""
    private val originalAppName by lazy { getString(R.string.app_name) }
    private val montserratBold: Typeface? by lazy { ResourcesCompat.getFont(this, R.font.montserrat_bold) }
    private val suggestionHandler = Handler(Looper.getMainLooper())
    private var suggestionRunnable: Runnable? = null
    private val rotateOpen: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate_forward) }
    private val rotateClose: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate_backward) }
    private val fromBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fab_open) }
    private val toBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fab_close) }
    private var isFabMenuOpen = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkExactAlarmPermission()
        } else {
            showPermissionRationaleDialog(
                "Bildirim İzni Gerekli",
                "Hatırlatıcıları alabilmek için bildirimlere izin vermeniz önemlidir. Ayarlardan izni daha sonra açabilirsiniz."
            )
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                updateGreetingMessage()
                showSnackbar(getString(R.string.welcome_message_user, account.displayName))
                showAutoBackupPrompt { checkForBackupAndProceed() }
            } catch (e: ApiException) {
                Log.w(TAG, "Sign-in failed, code: " + e.statusCode)
                showSnackbar(getString(R.string.google_sign_in_failed), isError = true)
            }
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isFabMenuOpen) {
                toggleFabMenu()
            } else {
                finish()
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseAnalytics = Firebase.analytics
        updateGreetingMessage()
        setupTitleRunnable()
        setupCustomFontForToolbar()
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        repository = PaydayRepository(this)
        googleDriveManager = GoogleDriveManager(this)
        setSupportActionBar(binding.toolbar)
        setupRecyclerViews()
        setupListeners()
        setupObservers()
        binding.emptyStateView.emptyStateButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        titleHandler.post(titleRunnable)
        checkExactAlarmPermission()
    }

    override fun onPause() {
        super.onPause()
        titleHandler.removeCallbacks(titleRunnable)
        binding.toolbar.title = originalAppName
    }

    private fun setupCustomFontForToolbar() {
        binding.toolbar.post {
            for (i in 0 until binding.toolbar.childCount) {
                val view = binding.toolbar.getChildAt(i)
                if (view is TextView) {
                    if (view.text.toString() == binding.toolbar.title) {
                        view.typeface = montserratBold
                        break
                    }
                }
            }
        }
    }

    /**
     * Selamlama metnini günceller. İSİM KALDIRILDI.
     */
    private fun updateGreetingMessage() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        greetingMessage = when (hour) {
            in 5..11 -> getString(R.string.greeting_morning)   // 05:00 - 11:59 -> Günaydın
            in 12..17 -> getString(R.string.greeting_afternoon)  // 12:00 - 17:59 -> Tünaydın
            in 18..21 -> getString(R.string.greeting_evening)   // 18:00 - 21:59 -> İyi akşamlar
            else -> getString(R.string.greeting_night)          // 22:00 - 04:59 -> İyi geceler
        }
    }
    private fun setupTitleRunnable() {
        titleRunnable = Runnable {
            val nextTitle = if (isShowingGreeting) {
                originalAppName
            } else {
                greetingMessage.ifBlank { originalAppName }
            }
            binding.toolbar.title = nextTitle
            isShowingGreeting = !isShowingGreeting
            titleHandler.postDelayed(titleRunnable, 10000)
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                checkExactAlarmPermission()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            checkExactAlarmPermission()
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                initializeNotifications()
            } else {
                showPermissionRationaleDialog(
                    "Önemli Bir İzin Eksik",
                    "Uygulamanın kapalıyken bile tam zamanında bildirim gönderebilmesi için 'Alarmlar ve Hatırlatıcılar' izni gerekiyor. Lütfen bir sonraki ekranda Payday uygulaması için bu izni etkinleştirin.",
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                )
            }
        } else {
            initializeNotifications()
        }
    }

    private fun initializeNotifications() {
        NotificationScheduler.createNotificationChannel(this)
        NotificationScheduler.scheduleRepeatingExpenseReminders(this)
    }

    private fun showPermissionRationaleDialog(title: String, message: String, settingsAction: String? = null) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Daha Sonra") { dialog, _ -> dialog.dismiss() }

        if (settingsAction != null) {
            builder.setPositiveButton("Ayarlara Git") { _, _ ->
                val intent = Intent(settingsAction)
                if (settingsAction == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        builder.show()
    }

    private fun performActionWithSignIn(action: () -> Unit) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            action.invoke()
        } else {
            googleSignInLauncher.launch(GoogleDriveManager.getSignInIntent(this))
        }
    }

    private fun backupData() {
        lifecycleScope.launch {
            try {
                showSnackbar(getString(R.string.backup_started))
                val backupData = repository.getAllDataForBackup()
                val backupJson = gson.toJson(backupData)
                googleDriveManager.uploadFileContent(backupJson)
                repository.saveLastBackupTimestamp(System.currentTimeMillis())
                viewModel.triggerBackupHeroAchievement()
                showSnackbar(getString(R.string.backup_success))
            } catch (e: Exception) {
                Log.e(TAG, "Error during backup!", e)
                showSnackbar(getString(R.string.backup_failed), isError = true)
            }
        }
    }

    private fun restoreData() {
        lifecycleScope.launch {
            try {
                showSnackbar(getString(R.string.restore_started))
                val backupJson = googleDriveManager.downloadFileContent()
                if (backupJson != null) {
                    val backupData = gson.fromJson(backupJson, BackupData::class.java)
                    repository.restoreDataFromBackup(backupData)
                    viewModel.loadData()
                    showSnackbar(getString(R.string.restore_success))
                } else {
                    showSnackbar(getString(R.string.restore_failed), isError = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during restore!", e)
                showSnackbar(getString(R.string.restore_failed), isError = true)
            }
        }
    }

    private fun showAutoBackupPrompt(onComplete: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_backup_title)
            .setMessage(R.string.auto_backup_prompt_message)
            .setCancelable(false)
            .setPositiveButton(R.string.yes_turn_on) { _, _ ->
                lifecycleScope.launch {
                    repository.setAutoBackupEnabled(true)
                    onComplete()
                }
            }
            .setNegativeButton(R.string.no_thanks) { _, _ ->
                lifecycleScope.launch {
                    repository.setAutoBackupEnabled(false)
                    onComplete()
                }
            }
            .show()
    }

    private fun checkForBackupAndProceed() {
        lifecycleScope.launch {
            if (googleDriveManager.isBackupAvailable()) {
                showRestoreDialog()
            } else {
                showSnackbar(getString(R.string.sign_in_success_and_initial_backup))
                backupData()
            }
        }
    }

    private fun showRestoreDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_found_title)
            .setMessage(R.string.restore_confirmation_message_main)
            .setCancelable(false)
            .setPositiveButton(R.string.yes_restore) { _, _ ->
                restoreData()
            }
            .setNegativeButton(R.string.no_dont_touch) { dialog, _ ->
                dialog.dismiss()
                showSnackbar(getString(R.string.existing_data_preserved))
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_backup -> performActionWithSignIn(::backupData)
            R.id.action_restore -> performActionWithSignIn(::restoreData)
            R.id.action_achievements -> {
                startActivity(Intent(this, AchievementsActivity::class.java))
            }
            R.id.action_settings -> settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.action_reports -> startActivity(Intent(this, ReportsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            binding.mainProgressBar.visibility = View.GONE
            val isSetupComplete = state.daysLeftText.isNotBlank() && state.daysLeftText != getString(R.string.day_not_set_placeholder)
            binding.mainContentScrollView.visibility = if (isSetupComplete) View.VISIBLE else View.GONE
            binding.addTransactionFab.visibility = if (isSetupComplete) View.VISIBLE else View.GONE
            binding.emptyStateView.root.visibility = if (isSetupComplete) View.GONE else View.VISIBLE

            if (isSetupComplete) {
                updateUi(state)

                val result = viewModel.getPaydayResult()
                if (result != null) {
                    NotificationScheduler.schedulePaydayReminder(this, result.daysLeft)
                }
            }
        }

        viewModel.financialInsight.observe(this) { event ->
            event.getContentIfNotHandled()?.let { insight ->
                suggestionRunnable?.let { suggestionHandler.removeCallbacks(it) }

                if (insight != null) {
                    binding.suggestionTextView.text = insight
                    val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_suggestion)
                    binding.suggestionCardView.startAnimation(fadeIn)
                    binding.suggestionCardView.visibility = View.VISIBLE

                    suggestionRunnable = Runnable {
                        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out_suggestion)
                        fadeOut.setAnimationListener(object : Animation.AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {}
                            override fun onAnimationEnd(animation: Animation?) {
                                binding.suggestionCardView.visibility = View.GONE
                            }
                            override fun onAnimationRepeat(animation: Animation?) {}
                        })
                        binding.suggestionCardView.startAnimation(fadeOut)
                    }
                    suggestionHandler.postDelayed(suggestionRunnable!!, 8000)
                } else {
                    binding.suggestionCardView.visibility = View.GONE
                }
            }
        }

        viewModel.widgetUpdateEvent.observe(this) { event -> event.getContentIfNotHandled()?.let { updateAllWidgets() } }
        viewModel.newAchievementEvent.observe(this) { event -> event.getContentIfNotHandled()?.let { showAchievementSnackbar(it) } }

        viewModel.goalCompletedEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { goal ->
                showGoalCompletedDialog(goal)
            }
        }

        viewModel.showRestoreWarningEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.restore_warning_title)
                    .setMessage(R.string.restore_warning_message)
                    .setPositiveButton(R.string.go_to_settings) { _, _ ->
                        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                    }
                    .setNegativeButton(R.string.ok, null)
                    .show()
            }
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.transactionsForCurrentCycle.observe(this) { transactions ->
            transactionAdapter.submitList(transactions)
            val areTransactionsEmpty = transactions.isNullOrEmpty()
            binding.emptyTransactionsTextView.visibility = if (areTransactionsEmpty) View.VISIBLE else View.GONE
            binding.transactionsRecyclerView.visibility = if (areTransactionsEmpty) View.GONE else View.VISIBLE
            binding.transactionsTitle.visibility = if (areTransactionsEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun showGoalCompletedDialog(goal: SavingsGoal) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.goal_completed_dialog_title))
            .setMessage(getString(R.string.goal_completed_dialog_message, goal.name))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.goal_completed_dialog_positive)) { _, _ ->
                viewModel.deleteGoal(goal)
                showSnackbar(getString(R.string.goal_completed_snackbar_message, goal.name))
            }
            .setNegativeButton(getString(R.string.goal_completed_dialog_negative)) { _, _ ->
                viewModel.releaseFundsFromGoal(goal)
                showSnackbar(getString(R.string.funds_restored_snackbar_message, goal.name))
            }
            .show()
    }

    private fun updateUi(state: PaydayUiState) {
        binding.daysLeftTextView.text = state.daysLeftText
        binding.daysLeftSuffixTextView.text = state.daysLeftSuffix
        binding.countdownTitleTextView.text = getString(R.string.next_payday_countdown)
        binding.incomeTextView.text = state.incomeText
        binding.expensesTextView.text = state.expensesText
        binding.savingsTextView.text = state.savingsText
        binding.remainingTextView.text = state.remainingText
        savingsGoalAdapter.submitList(state.savingsGoals)
        binding.savingsGoalsTitleContainer.visibility = if (state.areGoalsVisible) View.VISIBLE else View.GONE
        binding.savingsGoalsRecyclerView.visibility = if (state.areGoalsVisible) View.VISIBLE else View.GONE

        if (state.areGoalsVisible) {
            val totalSavedAmount = state.savingsGoals.sumOf { it.savedAmount }
            binding.totalSavingsValueTextView.text = getString(R.string.total_savings_label, formatCurrency(totalSavedAmount))
            binding.totalSavingsValueTextView.visibility = View.VISIBLE
        } else {
            binding.totalSavingsValueTextView.visibility = View.GONE
        }

        if (state.carryOverAmount != 0L) {
            binding.carryOverContainer.visibility = View.VISIBLE
            if (state.carryOverAmount > 0) {
                binding.carryOverTitleTextView.text = getString(R.string.carry_over)
                binding.carryOverTextView.text = formatCurrency(state.carryOverAmount.toDouble())
                binding.carryOverTextView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                binding.carryOverTitleTextView.text = getString(R.string.carry_over_debt)
                binding.carryOverTextView.text = formatCurrency(abs(state.carryOverAmount).toDouble())
                binding.carryOverTextView.setTextColor(ContextCompat.getColor(this, R.color.red_500))
            }
        } else {
            binding.carryOverContainer.visibility = View.GONE
        }

        if (state.isPayday) {
            startConfettiEffect()
        }
    }

    private fun setupListeners() {
        binding.addTransactionFab.setOnClickListener { toggleFabMenu() }

        binding.addTransactionSecondaryFab.setOnClickListener {
            firebaseAnalytics.logEvent("add_expense_clicked", null)
            TransactionDialogFragment.newInstance(null).show(supportFragmentManager, TransactionDialogFragment.TAG)
            toggleFabMenu()
        }

        binding.addSavingsGoalFab.setOnClickListener {
            firebaseAnalytics.logEvent("add_goal_clicked", null)
            SavingsGoalDialogFragment.newInstance(null).show(supportFragmentManager, SavingsGoalDialogFragment.TAG)
            toggleFabMenu()
        }
    }

    private fun toggleFabMenu() {
        if (isFabMenuOpen) {
            binding.addTransactionFab.startAnimation(rotateClose)
            binding.addTransactionSecondaryFab.startAnimation(toBottom)
            binding.addSavingsGoalFab.startAnimation(toBottom)
            binding.addTransactionFabLabel.startAnimation(toBottom)
            binding.addSavingsGoalFabLabel.startAnimation(toBottom)
            binding.addTransactionSecondaryFab.isClickable = false
            binding.addSavingsGoalFab.isClickable = false
            binding.addTransactionSecondaryFab.visibility = View.INVISIBLE
            binding.addSavingsGoalFab.visibility = View.INVISIBLE
            binding.addTransactionFabLabel.visibility = View.INVISIBLE
            binding.addSavingsGoalFabLabel.visibility = View.INVISIBLE
        } else {
            binding.addTransactionFab.startAnimation(rotateOpen)
            binding.addTransactionSecondaryFab.startAnimation(fromBottom)
            binding.addSavingsGoalFab.startAnimation(fromBottom)
            binding.addTransactionFabLabel.startAnimation(fromBottom)
            binding.addSavingsGoalFabLabel.startAnimation(fromBottom)
            binding.addTransactionSecondaryFab.visibility = View.VISIBLE
            binding.addSavingsGoalFab.visibility = View.VISIBLE
            binding.addTransactionFabLabel.visibility = View.VISIBLE
            binding.addSavingsGoalFabLabel.visibility = View.VISIBLE
            binding.addTransactionSecondaryFab.isClickable = true
            binding.addSavingsGoalFab.isClickable = true
        }
        isFabMenuOpen = !isFabMenuOpen
    }

    private fun setupRecyclerViews() {
        savingsGoalAdapter = SavingsGoalAdapter(
            onAddFundsClicked = { goal -> showAddFundsDialog(goal) },
            onEditClicked = { goal -> SavingsGoalDialogFragment.newInstance(goal.id).show(supportFragmentManager, SavingsGoalDialogFragment.TAG) },
            onDeleteClicked = { goal -> handleDeleteGoal(goal) }
        )
        binding.savingsGoalsRecyclerView.adapter = savingsGoalAdapter

        transactionAdapter = TransactionAdapter(
            onEditClicked = { transaction -> TransactionDialogFragment.newInstance(transaction.id).show(supportFragmentManager, TransactionDialogFragment.TAG) },
            onDeleteClicked = { transaction ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_transaction_confirmation_title)
                    .setMessage(R.string.delete_transaction_confirmation_message)
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.deleteTransaction(transaction) }
                    .show()
            }
        )
        binding.transactionsRecyclerView.adapter = transactionAdapter
    }

    private fun handleDeleteGoal(goal: SavingsGoal) {
        if (goal.savedAmount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.release_funds_title))
                .setMessage(getString(R.string.release_funds_message, goal.name, formatCurrency(goal.savedAmount)))
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.release_funds_button) { _, _ ->
                    viewModel.releaseFundsFromGoal(goal)
                    showSnackbar(getString(R.string.funds_released_and_goal_deleted))
                }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_goal_confirmation_title, goal.name))
                .setMessage(R.string.delete_goal_confirmation_message)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.deleteGoal(goal) }
                .show()
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount)
    }

    private fun showAddFundsDialog(goal: SavingsGoal) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_funds, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.amountEditText)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialogTitleTextView)
        val availableFundsTextView = dialogView.findViewById<TextView>(R.id.availableFundsTextView)

        val currentRemainingAmount = viewModel.uiState.value?.actualRemainingAmountForGoals ?: 0.0
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())

        titleTextView.text = getString(R.string.add_funds_to_goal_title, goal.name)
        availableFundsTextView.text = getString(R.string.available_funds_label, currencyFormatter.format(currentRemainingAmount))

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.add_funds) { _, _ ->
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    viewModel.addFundsToGoal(goal.id, amount)
                } else {
                    showSnackbar(getString(R.string.please_enter_valid_amount), isError = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateAllWidgets() {
        val intent = Intent(this, PaydayWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, PaydayWidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }

    private fun startConfettiEffect() {
        binding.konfettiView.start(
            Party(
                speed = 0f, maxSpeed = 30f, damping = 0.9f, spread = 360,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                position = Position.Relative(0.5, 0.3)
            )
        )
    }

    private fun showAchievementSnackbar(achievement: Achievement) {
        val snackbar = Snackbar.make(binding.coordinatorLayout, "", Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.addTransactionFab
        val snackbarLayout = snackbar.view as ViewGroup
        snackbarLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        snackbarLayout.setPadding(0, 0, 0, 0)
        val customView = layoutInflater.inflate(R.layout.toast_achievement_unlocked, snackbarLayout, false)
        customView.findViewById<ImageView>(R.id.toast_icon).setImageResource(achievement.iconResId)
        customView.findViewById<TextView>(R.id.toast_achievement_name).text = getString(achievement.titleResId)
        snackbarLayout.addView(customView, 0)
        snackbar.show()
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.red_500))
        }
        snackbar.show()
    }
}