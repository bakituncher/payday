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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.payday.databinding.ActivityMainBinding
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
                // İzin verildi, gelecekte bildirimler çalışacaktır.
            }
        }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar'ı Action Bar olarak ayarla
        setSupportActionBar(binding.toolbar)

        setupRecyclerViews()
        setupListeners()
        setupObservers()
        createNotificationChannel()
    }

    private fun setupListeners() {
        // DialogFragment'a taşındığı için bu metod artık daha temiz.
        binding.addGoalButton.setOnClickListener {
            SavingsGoalDialogFragment.newInstance(null).show(supportFragmentManager, SavingsGoalDialogFragment.TAG)
        }

        binding.addTransactionFab.setOnClickListener {
            // Yeni harcama için null ID ile çağır
            TransactionDialogFragment.newInstance(null).show(supportFragmentManager, TransactionDialogFragment.TAG)
        }
    }

    private fun setupRecyclerViews() {
        savingsGoalAdapter = SavingsGoalAdapter(
            onEditClicked = { goal ->
                // Düzenleme için DialogFragment'ı ID ile çağır
                SavingsGoalDialogFragment.newInstance(goal.id).show(supportFragmentManager, SavingsGoalDialogFragment.TAG)
            },
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
                // Düzenleme için DialogFragment'ı ID ile çağır
                TransactionDialogFragment.newInstance(transaction.id).show(supportFragmentManager, TransactionDialogFragment.TAG)
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
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
            }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_reports -> {
                startActivity(Intent(this, ReportsActivity::class.java))
                true
            }
            R.id.action_achievements -> {
                startActivity(Intent(this, AchievementsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}