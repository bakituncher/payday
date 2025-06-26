package com.example.payday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.payday.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var savingsGoalAdapter: SavingsGoalAdapter
    private lateinit var transactionAdapter: TransactionAdapter

    private val notificationId = 1
    private val channelId = "payday_channel"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // İzin verildi.
            }
        }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupListeners()
        setupObservers()
        createNotificationChannel()
    }

    private fun setupListeners() {
        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.addGoalButton.setOnClickListener { showGoalDialog() }
        binding.achievementsButton.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
        binding.addTransactionFab.setOnClickListener {
            showTransactionDialog()
        }

        binding.reportsButton.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
    }

    private fun setupRecyclerViews() {
        savingsGoalAdapter = SavingsGoalAdapter(
            onEditClicked = { goal -> showGoalDialog(goal) },
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
            onEditClicked = { transaction ->
                showTransactionDialog(transaction)
            },
            onDeleteClicked = { transaction ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_transaction_confirmation_title)
                    .setMessage(R.string.delete_transaction_confirmation_message)
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        viewModel.deleteTransaction(transaction)
                    }
                    .show()
            }
        )
        binding.transactionsRecyclerView.adapter = transactionAdapter
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            updateUi(state)
        }
        viewModel.widgetUpdateEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                updateAllWidgets()
            }
        }
        viewModel.newAchievementEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { achievement ->
                showAchievementSnackbar(achievement)
            }
        }
        viewModel.allTransactions.observe(this) { transactions ->
            transactionAdapter.submitList(transactions)
            binding.emptyTransactionsTextView.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
            binding.transactionsRecyclerView.visibility = if (transactions.isEmpty()) View.GONE else View.VISIBLE
            binding.transactionsTitle.visibility = if (transactions.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun updateAllWidgets() {
        val intent = Intent(this, PaydayWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(application)
                .getAppWidgetIds(ComponentName(application, PaydayWidgetProvider::class.java))
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

        savingsGoalAdapter.actualAmountAvailableForGoals = state.actualRemainingAmountForGoals
        savingsGoalAdapter.submitList(state.savingsGoals)

        val hasGoals = state.savingsGoals.isNotEmpty()
        binding.savingsGoalsTitleContainer.visibility = if (hasGoals) View.VISIBLE else View.GONE
        binding.savingsGoalsRecyclerView.visibility = if (hasGoals) View.VISIBLE else View.GONE

        if (state.isPayday) {
            startConfettiEffect()
            sendPaydayNotification()
        }
    }

    // DÜZELTME: Bu fonksiyon artık kategori seçim mantığını da içeriyor.
    private fun showTransactionDialog(existingTransaction: Transaction? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_input, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.transactionNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.transactionAmountEditText)
        val categoryChipGroup = dialogView.findViewById<ChipGroup>(R.id.categoryChipGroup)
        var selectedCategoryId = existingTransaction?.categoryId ?: ExpenseCategory.OTHER.ordinal

        // Kategorileri Chip olarak diyaloğa ekle
        ExpenseCategory.values().forEach { category ->
            val chip = Chip(this).apply {
                text = category.categoryName
                id = category.ordinal // Her chip'e ID olarak enum'un sırasını veriyoruz.
                isCheckable = true
                isChecked = (id == selectedCategoryId) // Düzenleme modunda doğru chip'i seçili yap.
            }
            categoryChipGroup.addView(chip)
        }

        // Chip seçimini dinle
        categoryChipGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedCategoryId = if (checkedId != View.NO_ID) checkedId else ExpenseCategory.OTHER.ordinal
        }


        val dialogTitleRes = if (existingTransaction == null) R.string.add_transaction else R.string.edit_transaction_title

        existingTransaction?.let {
            nameEditText.setText(it.name)
            amountEditText.setText(it.amount.toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(dialogTitleRes)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (name.isNotBlank() && amount != null && amount > 0) {
                    if (existingTransaction == null) {
                        viewModel.insertTransaction(name, amount, selectedCategoryId)
                    } else {
                        viewModel.updateTransaction(existingTransaction.id, name, amount, selectedCategoryId)
                    }
                } else {
                    Toast.makeText(this, "Lütfen geçerli bir ad ve tutar girin.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showGoalDialog(existingGoal: SavingsGoal? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_goal_input, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.goalNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.goalAmountEditText)

        val dialogTitle = if (existingGoal == null) getString(R.string.dialog_add_goal_title) else getString(R.string.dialog_edit_goal_title)
        existingGoal?.let {
            nameEditText.setText(it.name)
            amountEditText.setText(it.targetAmount.toLong().toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()
                if (name.isNotBlank() && amount != null && amount > 0) {
                    viewModel.addOrUpdateGoal(name, amount, existingGoal?.id)
                } else {
                    Toast.makeText(this, getString(R.string.toast_invalid_goal_input), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply { description = descriptionText }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendPaydayNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.payday_notification_title))
            .setContentText(getString(R.string.payday_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }

    private fun showAchievementSnackbar(achievement: Achievement) {
        val snackbar = Snackbar.make(binding.coordinatorLayout, "", Snackbar.LENGTH_LONG)
        val snackbarLayout = snackbar.view as ViewGroup

        snackbarLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        snackbarLayout.setPadding(0, 0, 0, 0)

        val customView = layoutInflater.inflate(R.layout.toast_achievement_unlocked, null)

        customView.findViewById<ImageView>(R.id.toast_icon).setImageResource(achievement.iconResId)
        customView.findViewById<TextView>(R.id.toast_achievement_name).text = achievement.title

        snackbarLayout.addView(customView, 0)
        snackbar.show()
    }
}
