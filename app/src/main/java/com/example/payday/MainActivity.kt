package com.example.payday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.payday.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefsName = "PaydayPrefs"
    private val paydayKey = "PaydayOfMonth"
    private val weekendAdjustmentKey = "WeekendAdjustmentEnabled"

    private val CHANNEL_ID = "payday_channel"
    private val NOTIFICATION_ID = 1

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

        createNotificationChannel()
        setupListeners()
        loadSettings()

        updateCountdown()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isWeekendAdjustmentEnabled = prefs.getBoolean(weekendAdjustmentKey, false)
        binding.weekendAdjustmentSwitch.isChecked = isWeekendAdjustmentEnabled
    }

    private fun setupListeners() {
        binding.setPaydayButton.setOnClickListener {
            showPaydaySelectionDialog()
        }

        binding.weekendAdjustmentSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveWeekendAdjustmentSetting(isChecked)
            updateCountdown(true) // Animasyon için zorla güncelleme
            updateAllWidgets()
        }
    }

    // --- YENİ KONFETİ FONKSİYONU ---
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
        // ... (Bu fonksiyon değişmedi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadPayday(): Int {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.getInt(paydayKey, -1)
    }

    private fun savePayday(dayOfMonth: Int) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putInt(paydayKey, dayOfMonth)
        }
    }

    private fun saveWeekendAdjustmentSetting(isEnabled: Boolean) {
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putBoolean(weekendAdjustmentKey, isEnabled)
        }
    }

    private fun sendPaydayNotification() {
        // ... (Bu fonksiyon değişmedi)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.payday_notification_title))
            .setContentText(getString(R.string.payday_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    // --- ANİMASYONLU GÜNCELLEME İÇİN DEĞİŞTİRİLDİ ---
    private fun updateCountdown(forceUpdate: Boolean = false) {
        val result = PaydayCalculator.calculate(this)

        // Mevcut metni al, eğer değişmeyecekse animasyon yapma
        val currentText = binding.daysLeftTextView.text.toString()

        val newText = result?.daysLeft?.toString() ?: if (loadPayday() == -1) getString(R.string.day_not_set_placeholder) else "!!"
        if (currentText == newText && !forceUpdate) return // Değişiklik yoksa çık

        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // Kaybolma animasyonu bitince metinleri güncelle
                if (result == null) {
                    val paydayOfMonth = loadPayday()
                    if (paydayOfMonth == -1) {
                        binding.daysLeftTextView.text = getString(R.string.day_not_set_placeholder)
                        binding.daysLeftSuffixTextView.text = getString(R.string.welcome_message)
                    } else {
                        binding.daysLeftTextView.text = "!!"
                        binding.daysLeftSuffixTextView.text = getString(R.string.invalid_day_error)
                    }
                    binding.daysLeftSuffixTextView.visibility = View.VISIBLE
                } else {
                    if (result.isPayday) {
                        binding.daysLeftTextView.text = "" // Konfeti için boşalt
                        binding.daysLeftSuffixTextView.text = getString(R.string.payday_is_today)
                        startConfettiEffect() // Konfetiyi başlat!
                        sendPaydayNotification()
                    } else {
                        binding.daysLeftTextView.text = result.daysLeft.toString()
                        binding.daysLeftSuffixTextView.text = getString(R.string.days_left_suffix)
                    }
                    binding.daysLeftSuffixTextView.visibility = View.VISIBLE
                }
                // Belirme animasyonunu başlat
                val fadeIn = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in)
                binding.cardView.startAnimation(fadeIn)
            }
        })
        binding.cardView.startAnimation(fadeOut)
    }

    private fun showPaydaySelectionDialog() {
        val days = (1..31).map { it.toString() }.toTypedArray()
        val currentPayday = loadPayday()
        val checkedItem = if (currentPayday != -1) currentPayday - 1 else 0

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_payday_dialog_title))
            .setSingleChoiceItems(days, checkedItem) { dialog, which ->
                val selectedDay = which + 1
                savePayday(selectedDay)
                updateCountdown(true) // Animasyon için zorla güncelleme
                updateAllWidgets()
                dialog.dismiss()
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateAllWidgets() {
        // ... (Bu fonksiyon değişmedi)
        val intent = Intent(this, PaydayWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(application)
                .getAppWidgetIds(ComponentName(application, PaydayWidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }
}