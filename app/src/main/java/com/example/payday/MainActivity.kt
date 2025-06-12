package com.example.payday

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.payday.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    // View Binding'i kuruyoruz. Bu, arayüz elemanlarına güvenli erişim sağlar.
    private lateinit var binding: ActivityMainBinding

    // Değişken isimlendirme kurallarına uyarak isimleri güncelledik.
    private val prefsName = "PaydayPrefs"
    private val paydayKey = "PaydayOfMonth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Arayüzü View Binding ile yüklüyoruz.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Butona tıklanma olayını dinliyoruz. Artık 'binding' üzerinden erişiyoruz.
        binding.setPaydayButton.setOnClickListener {
            showPaydaySelectionDialog()
        }

        // Geri sayımı güncelleyen ana fonksiyonu çağırıyoruz.
        updateCountdown()
    }

    // Cihaz hafızasından kayıtlı maaş gününü yükler.
    private fun loadPayday(): Int {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.getInt(paydayKey, -1)
    }

    // Seçilen maaş gününü cihaz hafızasına kaydeder.
    private fun savePayday(dayOfMonth: Int) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        // Kotlin KTX ile daha temiz ve kısa kayıt yapıyoruz.
        prefs.edit {
            putInt(paydayKey, dayOfMonth)
        }
    }

    // Geri sayımı hesaplayan ve ekrana yazan fonksiyon.
    private fun updateCountdown() {
        val paydayOfMonth = loadPayday()

        if (paydayOfMonth == -1) {
            binding.daysLeftTextView.text = getString(R.string.day_not_set_placeholder)
            binding.daysLeftSuffixTextView.visibility = View.VISIBLE
            return
        }

        // Artık bu kodlar API seviye hatası vermeyecek.
        val today = LocalDate.now()
        val nextPayday: LocalDate

        val paydayInCurrentMonth = today.withDayOfMonth(paydayOfMonth)

        nextPayday = if (today.isAfter(paydayInCurrentMonth)) {
            today.plusMonths(1).withDayOfMonth(paydayOfMonth)
        } else {
            paydayInCurrentMonth
        }

        val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)

        if (daysLeft == 0L) {
            binding.daysLeftTextView.text = "🎉"
            binding.daysLeftSuffixTextView.text = getString(R.string.payday_is_today)
        } else {
            binding.daysLeftTextView.text = daysLeft.toString()
            binding.daysLeftSuffixTextView.text = getString(R.string.days_left_suffix)
            binding.daysLeftSuffixTextView.visibility = View.VISIBLE
        }
    }

    // Kullanıcının maaş gününü seçebileceği bir dialog penceresi gösterir.
    private fun showPaydaySelectionDialog() {
        val days = (1..31).map { it.toString() }.toTypedArray()
        val currentPayday = loadPayday()
        val checkedItem = if (currentPayday != -1) currentPayday - 1 else 0

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_payday_dialog_title))
            .setSingleChoiceItems(days, checkedItem) { dialog, which ->
                val selectedDay = which + 1
                savePayday(selectedDay)
                updateCountdown()
                dialog.dismiss()
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}