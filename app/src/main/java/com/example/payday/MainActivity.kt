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
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.payday.databinding.ActivityMainBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var savingsGoalAdapter: SavingsGoalAdapter

    private val notificationId = 1
    private val channelId = "payday_channel"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // İzin verildi, gerekirse tekrar bildirim gönderme işlemi yapılabilir.
            }
        }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onSettingsResult()
            result.data?.getStringExtra("dialog_to_show")?.let { showDialogFromKey(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        setupObservers()
        createNotificationChannel()
    }

    private fun setupListeners() {
        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.addGoalButton.setOnClickListener { showGoalDialog() }
    }

    private fun setupRecyclerView() {
        savingsGoalAdapter = SavingsGoalAdapter(
            onEditClicked = { goal -> showGoalDialog(goal) },
            onDeleteClicked = { goal ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("${goal.name} Silinsin mi?")
                    .setMessage("Bu hedef kalıcı olarak silinecek.")
                    .setNegativeButton("İptal", null)
                    .setPositiveButton("Sil") { _, _ -> viewModel.deleteGoal(goal) }
                    .show()
            }
        )
        binding.savingsGoalsRecyclerView.adapter = savingsGoalAdapter
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            updateUi(state)
        }
        viewModel.events.observe(this) { event ->
            event.getContentIfNotHandled()?.let { eventKey ->
                showDialogFromKey(eventKey)
            }
        }
        viewModel.widgetUpdateEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                updateAllWidgets()
            }
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
        binding.accumulationAmountTextView.text = state.accumulatedAmountText

        savingsGoalAdapter.accumulatedAmountForGoals = state.accumulatedSavingsForGoals
        savingsGoalAdapter.submitList(state.savingsGoals)

        // YENİ: Grafik verisi varsa grafiği çiz
        if (state.chartData != null && state.chartData.isNotEmpty()) {
            binding.cashFlowChartView.visibility = View.VISIBLE
            setupChart(state.chartData)
        } else {
            binding.cashFlowChartView.visibility = View.GONE
        }

        if (state.isPayday) {
            startConfettiEffect()
            sendPaydayNotification()
        }
    }

    // YENİ FONKSİYON: Grafiği yapılandırmak ve çizmek için
    private fun setupChart(entries: List<Entry>) {
        val lineDataSet = LineDataSet(entries, "Birikim Grafiği").apply {
            // Çizgi Stili
            color = ContextCompat.getColor(this@MainActivity, R.color.primary)
            lineWidth = 3f

            // Nokta Stili
            setCircleColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
            circleRadius = 4f
            setDrawCircleHole(false)

            // Gradyan Alanı
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.chart_gradient)

            // Değerleri gösterme
            setDrawValues(false)

            // Vurgu çizgisi
            highLightColor = ContextCompat.getColor(this@MainActivity, R.color.primary_dark)

            // Mod
            mode = LineDataSet.Mode.CUBIC_BEZIER // Daha yumuşak geçişler için
        }

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(lineDataSet)
        val lineData = LineData(dataSets)

        binding.cashFlowChartView.apply {
            data = lineData

            // Genel Grafik Ayarları
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false) // Yakınlaştırmayı kapat
            setPinchZoom(false)
            setDrawGridBackground(false)

            // X Ekseni Ayarları
            xAxis.apply {
                isEnabled = false // X ekseni etiketlerini ve çizgisini gizle
            }

            // Sol Y Ekseni Ayarları
            axisLeft.apply {
                setDrawLabels(false) // Y ekseni etiketlerini gizle
                setDrawAxisLine(false) // Y ekseni çizgisini gizle
                setDrawGridLines(false) // Arka plan grid çizgilerini gizle
                axisMinimum = 0f // Grafiğin en alttan başlamasını sağla
            }

            // Sağ Y Ekseni Ayarları
            axisRight.isEnabled = false

            // Grafiği Yenile
            invalidate()
        }
    }


    private fun showDialogFromKey(key: String) {
        when (key) {
            "show_pay_period_dialog" -> showPayPeriodSelectionDialog()
            "show_payday_dialog", "show_dynamic_payday_selection" -> showDynamicPaydaySelectionDialog()
            "show_salary_dialog" -> showSalaryInputDialog()
            "show_monthly_savings_dialog" -> showSetMonthlySavingsDialog()
        }
    }

    private fun showSalaryInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_salary_input, null)
        val salaryEditText = dialogView.findViewById<EditText>(R.id.salaryEditText)
        val repository = PaydayRepository(this)
        val currentSalary = repository.getSalaryAmount()
        if (currentSalary > 0) salaryEditText.setText(currentSalary.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_set_salary_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                salaryEditText.text.toString().toLongOrNull()?.let {
                    viewModel.saveSalary(it)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSetMonthlySavingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_salary_input, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.salaryEditText)
        amountEditText.hint = getString(R.string.dialog_monthly_savings_hint)

        val repository = PaydayRepository(this)
        val currentAmount = repository.getMonthlySavingsAmount()
        if (currentAmount > 0) {
            amountEditText.setText(currentAmount.toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_set_monthly_savings_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val amount = amountEditText.text.toString().toLongOrNull() ?: 0L
                viewModel.saveMonthlySavings(amount)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showGoalDialog(existingGoal: SavingsGoal? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_goal_input, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.goalNameEditText)
        val amountEditText = dialogView.findViewById<EditText>(R.id.goalAmountEditText)

        val dialogTitle = if (existingGoal == null) getString(R.string.dialog_add_goal_title) else "Hedefi Düzenle"
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
                    Toast.makeText(this, "Lütfen geçerli bir ad ve tutar girin.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showPayPeriodSelectionDialog() {
        val repository = PaydayRepository(this)
        val currentPeriod = repository.getPayPeriod()
        val periods = resources.getStringArray(R.array.pay_periods)
        val checkedItem = currentPeriod.ordinal

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_pay_period_dialog_title))
            .setSingleChoiceItems(periods, checkedItem) { dialog, which ->
                val selectedPeriod = PayPeriod.values()[which]
                viewModel.savePayPeriod(selectedPeriod)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDynamicPaydaySelectionDialog() {
        val repository = PaydayRepository(this)
        when (repository.getPayPeriod()) {
            PayPeriod.MONTHLY -> {
                val days = (1..31).map { it.toString() }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.select_payday_dialog_title))
                    .setItems(days) { dialog, which ->
                        viewModel.savePayday(which + 1)
                        dialog.dismiss()
                    }.show()
            }
            PayPeriod.WEEKLY -> {
                val daysOfWeek = resources.getStringArray(R.array.days_of_week)
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.select_payday_dialog_title))
                    .setItems(daysOfWeek) { dialog, which ->
                        viewModel.savePayday(which + 1)
                        dialog.dismiss()
                    }.show()
            }
            PayPeriod.BI_WEEKLY -> {
                val c = Calendar.getInstance()
                DatePickerDialog(this, { _, year, month, day ->
                    viewModel.saveBiWeeklyReferenceDate(LocalDate.of(year, month + 1, day))
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
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
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun sendPaydayNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.payday_notification_title))
            .setContentText(getString(R.string.payday_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(this)) { notify(notificationId, builder.build()) }
    }
}
