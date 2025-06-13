package com.example.payday

import android.Manifest
import android.app.DatePickerDialog
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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.payday.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // SharedPreferences Anahtarları
    private val prefsName = "PaydayPrefs"
    private val paydayKey = "PaydayOfMonth"
    private val weekendAdjustmentKey = "WeekendAdjustmentEnabled"
    private val salaryKey = "SalaryAmount"
    private val payPeriodKey = "PayPeriod"
    private val biWeeklyRefDateKey = "BiWeeklyRefDate"
    private val savingsGoalsKey = "SavingsGoals" // YENİ HEDEF ANAHTARI

    // Bildirim Ayarları
    private val channelId = "payday_channel"
    private val notificationId = 1

    // YENİ: Tasarruf Hedefleri için Değişkenler
    private lateinit var savingsGoalAdapter: SavingsGoalAdapter
    private val gson = Gson()
    private var currentGoals: MutableList<SavingsGoal> = mutableListOf()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                updateCountdown()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView() // YENİ
        createNotificationChannel()
        setupListeners()
        loadSettings()
        loadGoals() // YENİ
        updateCountdown()
    }

    // YENİ: RecyclerView'ı hazırlar
    private fun setupRecyclerView() {
        savingsGoalAdapter = SavingsGoalAdapter()
        binding.savingsGoalsRecyclerView.adapter = savingsGoalAdapter
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        binding.weekendAdjustmentSwitch.isChecked = prefs.getBoolean(weekendAdjustmentKey, false)
    }

    private fun setupListeners() {
        binding.setPaydayButton.setOnClickListener {
            showDynamicPaydaySelectionDialog()
        }
        binding.setPayPeriodButton.setOnClickListener {
            showPayPeriodSelectionDialog()
        }
        binding.weekendAdjustmentSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveWeekendAdjustmentSetting(isChecked)
            updateCountdown(true)
            updateAllWidgets()
        }
        binding.setSalaryButton.setOnClickListener {
            showSalaryInputDialog()
        }
        // YENİ: Yeni hedef ekleme butonu için listener
        binding.addGoalButton.setOnClickListener {
            showAddGoalDialog()
        }
    }

    // ... startConfettiEffect ve createNotificationChannel fonksiyonları aynı kalıyor ...
    private fun startConfettiEffect() {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
            position = Position.Relative(0.5, 0.3)
        )
        binding.konfettiView.start(party)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // --- Kaydetme Fonksiyonları ---
    private fun savePayday(day: Int) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putInt(paydayKey, day)
            // Bi-weekly ayarını temizle
            remove(biWeeklyRefDateKey)
        }
    }

    private fun savePayPeriod(payPeriod: PayPeriod) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putString(payPeriodKey, payPeriod.name)
        }
    }

    private fun saveBiWeeklyReferenceDate(date: LocalDate) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putString(biWeeklyRefDateKey, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            remove(paydayKey)
        }
    }

    private fun saveWeekendAdjustmentSetting(isEnabled: Boolean) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putBoolean(weekendAdjustmentKey, isEnabled)
        }
    }

    private fun saveSalary(salary: Long) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putLong(salaryKey, salary)
        }
    }

    // YENİ: Tasarruf hedeflerini JSON olarak kaydeder
    private fun saveGoals() {
        val jsonGoals = gson.toJson(currentGoals)
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putString(savingsGoalsKey, jsonGoals)
        }
    }

    // YENİ: Tasarruf hedeflerini JSON'dan yükler
    private fun loadGoals() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val jsonGoals = prefs.getString(savingsGoalsKey, null)
        if (jsonGoals != null) {
            val type = object : TypeToken<MutableList<SavingsGoal>>() {}.type
            currentGoals = gson.fromJson(jsonGoals, type)
        }
        updateGoalsUI(0.0) // Başlangıçta 0 birikimle UI'ı güncelle
    }

    // YENİ: Hedefler listesinin görünürlüğünü ve adaptörünü günceller
    private fun updateGoalsUI(accumulatedAmount: Double) {
        if (currentGoals.isEmpty()) {
            binding.savingsGoalsTitle.visibility = View.GONE
            binding.savingsGoalsRecyclerView.visibility = View.GONE
        } else {
            binding.savingsGoalsTitle.visibility = View.VISIBLE
            binding.savingsGoalsRecyclerView.visibility = View.VISIBLE
        }
        savingsGoalAdapter.submitList(currentGoals, accumulatedAmount)
    }

    private fun sendPaydayNotification() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

    private fun updateCountdown(forceUpdate: Boolean = false) {
        val result = PaydayCalculator.calculate(this)

        val currentDaysText = binding.daysLeftTextView.text.toString()
        val newDaysText = result?.daysLeft?.toString() ?: "..."
        if (currentDaysText == newDaysText && !forceUpdate) return

        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                val accumulatedAmount = result?.accumulatedAmount ?: 0.0
                if (result == null) {
                    val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    if (!prefs.contains(paydayKey) && !prefs.contains(biWeeklyRefDateKey)) {
                        binding.daysLeftTextView.text = getString(R.string.day_not_set_placeholder)
                        binding.daysLeftSuffixTextView.text = getString(R.string.welcome_message)
                    } else {
                        binding.daysLeftTextView.text = "!!"
                        binding.daysLeftSuffixTextView.text = getString(R.string.invalid_day_error)
                    }
                    binding.accumulationAmountTextView.text = formatCurrency(0.0)
                } else {
                    if (result.isPayday) {
                        binding.daysLeftTextView.text = ""
                        binding.daysLeftSuffixTextView.text = getString(R.string.payday_is_today)
                        startConfettiEffect()
                        sendPaydayNotification()
                    } else {
                        binding.daysLeftTextView.text = result.daysLeft.toString()
                        binding.daysLeftSuffixTextView.text = getString(R.string.days_left_suffix)
                    }
                    binding.accumulationAmountTextView.text = formatCurrency(accumulatedAmount)
                }

                // YENİ: Hedefler UI'ını güncel birikimle yenile
                updateGoalsUI(accumulatedAmount)

                val fadeIn = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in)
                binding.cardView.startAnimation(fadeIn)
                binding.accumulationCardView.startAnimation(fadeIn)
            }
        })
        binding.cardView.startAnimation(fadeOut)
        binding.accumulationCardView.startAnimation(fadeOut)
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
        return format.format(amount)
    }

    // --- Diyalog Fonksiyonları ---

    // YENİ: Yeni hedef ekleme diyalogunu gösterir
    private fun showAddGoalDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_goal_input, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.goalNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.goalAmountEditText)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_add_goal_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val name = nameEditText.text.toString()
                val amount = amountEditText.text.toString().toDoubleOrNull()

                if (name.isNotBlank() && amount != null && amount > 0) {
                    val newGoal = SavingsGoal(name = name, targetAmount = amount)
                    currentGoals.add(newGoal)
                    saveGoals()
                    updateGoalsUI(binding.accumulationAmountTextView.text.toString().filter { it.isDigit() }.toDoubleOrNull() ?: 0.0)
                } else {
                    Toast.makeText(this, "Lütfen geçerli bir ad ve tutar girin.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSalaryInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_salary_input, null)
        val salaryEditText = dialogView.findViewById<EditText>(R.id.salaryEditText)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_set_salary_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val salaryString = salaryEditText.text.toString()
                if (salaryString.isNotBlank()) {
                    salaryString.toLongOrNull()?.let {
                        saveSalary(it)
                        updateCountdown(true)
                        updateAllWidgets()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDynamicPaydaySelectionDialog() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val currentPeriod = PayPeriod.valueOf(prefs.getString(payPeriodKey, PayPeriod.MONTHLY.name)!!)

        when (currentPeriod) {
            PayPeriod.MONTHLY -> {
                val currentPayday = prefs.getInt(paydayKey, -1)
                val days = (1..31).map { it.toString() }.toTypedArray()
                val checkedItem = if (currentPayday != -1) currentPayday - 1 else 0

                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.select_payday_dialog_title))
                    .setSingleChoiceItems(days, checkedItem) { dialog, which ->
                        val selectedDay = which + 1
                        savePayday(selectedDay)
                        updateCountdown(true)
                        updateAllWidgets()
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            PayPeriod.WEEKLY -> {
                val currentPayday = prefs.getInt(paydayKey, 1)
                val daysOfWeek = resources.getStringArray(R.array.days_of_week)
                val checkedItem = if (currentPayday in 1..7) currentPayday - 1 else 0

                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.select_payday_dialog_title))
                    .setSingleChoiceItems(daysOfWeek, checkedItem) { dialog, which ->
                        savePayday(which + 1)
                        updateCountdown(true)
                        updateAllWidgets()
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            PayPeriod.BI_WEEKLY -> {
                val calendar = Calendar.getInstance()
                val datePickerDialog = DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                        saveBiWeeklyReferenceDate(selectedDate)
                        updateCountdown(true)
                        updateAllWidgets()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                datePickerDialog.setTitle(getString(R.string.select_biweekly_ref_date_dialog_title))
                datePickerDialog.show()
            }
        }
    }

    private fun showPayPeriodSelectionDialog() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val currentPeriodName = prefs.getString(payPeriodKey, PayPeriod.MONTHLY.name)
        val currentPeriod = PayPeriod.valueOf(currentPeriodName!!)
        val periods = resources.getStringArray(R.array.pay_periods)
        val checkedItem = currentPeriod.ordinal

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_pay_period_dialog_title))
            .setSingleChoiceItems(periods, checkedItem) { dialog, which ->
                val selectedPeriod = PayPeriod.values()[which]
                savePayPeriod(selectedPeriod)
                showDynamicPaydaySelectionDialog()
                updateCountdown(true)
                updateAllWidgets()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
}