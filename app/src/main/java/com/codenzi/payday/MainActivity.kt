package com.codenzi.payday

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var savingsGoalAdapter: SavingsGoalAdapter
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var repository: PaydayRepository
    private lateinit var googleDriveManager: GoogleDriveManager
    private val gson = Gson()
    private var pendingAction: (() -> Unit)? = null
    private val TAG = "PaydayBackup"

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Giriş başarılı. Bekleyen işlem çalıştırılıyor.")
            pendingAction?.invoke()
            pendingAction = null
        } else {
            Log.w(TAG, "Giriş başarısız oldu veya kullanıcı tarafından iptal edildi.")
            Toast.makeText(this, "Google ile giriş başarısız oldu.", Toast.LENGTH_SHORT).show()
            pendingAction = null
        }
    }

    private val rotateOpen: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate_forward) }
    private val rotateClose: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate_backward) }
    private val fromBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fab_open) }
    private val toBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fab_close) }
    private var isFabMenuOpen = false
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tema yükleme kodları buradan tamamen kaldırılmıştır.
        // Bu işlem artık PaydayApplication sınıfı tarafından yönetilmektedir.

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PaydayRepository(this)
        googleDriveManager = GoogleDriveManager(this)
        setSupportActionBar(binding.toolbar)
        setupRecyclerViews()
        setupListeners()
        setupObservers()
    }

    private fun performActionWithSignIn(action: () -> Unit) {
        if (GoogleSignIn.getLastSignedInAccount(this) == null) {
            pendingAction = action
            googleSignInLauncher.launch(GoogleDriveManager.getSignInIntent(this))
        } else {
            action.invoke()
        }
    }

    private fun backupData() {
        performActionWithSignIn {
            lifecycleScope.launch {
                try {
                    Toast.makeText(this@MainActivity, "Yedekleme başlatıldı...", Toast.LENGTH_SHORT).show()
                    val backupData = repository.getAllDataForBackup()
                    val backupJson = gson.toJson(backupData)
                    googleDriveManager.uploadFileContent(backupJson)
                    Toast.makeText(this@MainActivity, R.string.backup_success, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Yedekleme işlemi sırasında HATA!", e)
                    Toast.makeText(this@MainActivity, R.string.backup_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun restoreData() {
        performActionWithSignIn {
            lifecycleScope.launch {
                try {
                    Toast.makeText(this@MainActivity, "Geri yükleme başlatıldı...", Toast.LENGTH_SHORT).show()
                    val backupJson = googleDriveManager.downloadFileContent()
                    if (backupJson != null) {
                        val backupData = gson.fromJson(backupJson, BackupData::class.java)
                        repository.restoreDataFromBackup(backupData)
                        viewModel.loadData()
                        Toast.makeText(this@MainActivity, R.string.restore_success, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.restore_failed, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Geri yükleme işlemi sırasında HATA!", e)
                    Toast.makeText(this@MainActivity, R.string.restore_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_backup -> backupData()
            R.id.action_restore -> restoreData()
            R.id.action_settings -> settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.action_reports -> startActivity(Intent(this, ReportsActivity::class.java))
            R.id.action_achievements -> startActivity(Intent(this, AchievementsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun setupListeners() {
        binding.addTransactionFab.setOnClickListener { toggleFabMenu() }
        binding.addTransactionSecondaryFab.setOnClickListener {
            TransactionDialogFragment.newInstance(null).show(supportFragmentManager, TransactionDialogFragment.TAG)
            toggleFabMenu()
        }
        binding.addSavingsGoalFab.setOnClickListener {
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
            onDeleteClicked = { goal ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_goal_confirmation_title, goal.name))
                    .setMessage(R.string.delete_goal_confirmation_message)
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.deleteGoal(goal) }
                    .show()
            }
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
    private fun setupObservers() {
        viewModel.uiState.observe(this) { state -> updateUi(state) }
        viewModel.widgetUpdateEvent.observe(this) { event -> event.getContentIfNotHandled()?.let { updateAllWidgets() } }
        viewModel.newAchievementEvent.observe(this) { event -> event.getContentIfNotHandled()?.let { achievement -> showAchievementSnackbar(achievement) } }
        viewModel.transactionsForCurrentCycle.observe(this) { transactions ->
            transactionAdapter.submitList(transactions)
            val areTransactionsEmpty = transactions.isNullOrEmpty()
            binding.emptyTransactionsTextView.visibility = if (areTransactionsEmpty) View.VISIBLE else View.GONE
            binding.transactionsRecyclerView.visibility = if (areTransactionsEmpty) View.GONE else View.VISIBLE
            binding.transactionsTitle.visibility = if (areTransactionsEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun showAddFundsDialog(goal: SavingsGoal) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_funds, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.amountEditText)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialogTitleTextView)
        val availableFundsTextView = dialogView.findViewById<TextView>(R.id.availableFundsTextView)

        val currentRemainingAmount = viewModel.uiState.value?.actualRemainingAmountForGoals ?: 0.0
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

        titleTextView.text = "'${goal.name}' hedefine para ekle"
        availableFundsTextView.text = "Kullanılabilir Bakiye: ${currencyFormatter.format(currentRemainingAmount)}"

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Ekle") { _, _ ->
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    if (amount > currentRemainingAmount) {
                        Toast.makeText(this, "Yetersiz bakiye!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addFundsToGoal(goal.id, amount)
                    }
                } else {
                    Toast.makeText(this, "Lütfen geçerli bir tutar girin.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
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

    private fun updateUi(state: PaydayUiState) {
        binding.daysLeftTextView.text = state.daysLeftText
        binding.daysLeftSuffixTextView.text = state.daysLeftSuffix
        binding.countdownTitleTextView.text = getString(R.string.next_payday_countdown)
        binding.incomeTextView.text = state.incomeText
        binding.expensesTextView.text = state.expensesText
        binding.remainingTextView.text = state.remainingText
        savingsGoalAdapter.submitList(state.savingsGoals)
        binding.savingsGoalsTitleContainer.visibility = if (state.areGoalsVisible) View.VISIBLE else View.GONE
        binding.savingsGoalsRecyclerView.visibility = if (state.areGoalsVisible) View.VISIBLE else View.GONE
        if (state.isPayday) {
            startConfettiEffect()
        }
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
        val snackbarLayout = snackbar.view as ViewGroup
        snackbarLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        snackbarLayout.setPadding(0, 0, 0, 0)
        val customView = layoutInflater.inflate(R.layout.toast_achievement_unlocked, snackbarLayout, false)
        customView.findViewById<ImageView>(R.id.toast_icon).setImageResource(achievement.iconResId)
        customView.findViewById<TextView>(R.id.toast_achievement_name).text = achievement.title
        snackbarLayout.addView(customView, 0)
        snackbar.show()
    }
}