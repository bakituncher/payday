package com.example.payday

import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.*

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var repository: PaydayRepository
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        repository = PaydayRepository(requireContext())

        // Maaş günü seçimi için dinleyici
        findPreference<Preference>(PaydayRepository.KEY_PAYDAY_VALUE)?.setOnPreferenceClickListener {
            showPaydaySelectionDialog()
            true
        }

        // DÜZELTME: Maaş ve Tasarruf alanlarının doğru şekilde (sayı olarak) kaydedilmesini sağlıyoruz.
        setupCurrencyPreference(PaydayRepository.KEY_SALARY)
        setupCurrencyPreference(PaydayRepository.KEY_MONTHLY_SAVINGS)
    }

    private fun setupCurrencyPreference(key: String) {
        val preference = findPreference<EditTextPreference>(key)
        preference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        // Değer değiştiğinde, metin olarak değil, sayı (Long) olarak kaydedilmesini sağlıyoruz.
        preference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
            val valueAsLong = (newValue as? String)?.toLongOrNull() ?: 0L

            if (key == PaydayRepository.KEY_SALARY) {
                repository.saveSalary(valueAsLong)
            } else if (key == PaydayRepository.KEY_MONTHLY_SAVINGS) {
                repository.saveMonthlySavings(valueAsLong)
            }

            // Özeti manuel olarak güncelliyoruz.
            pref.summary = formatToCurrency(valueAsLong)

            // false dönerek EditTextPreference'ın kendisinin metin olarak kaydetmesini engelliyoruz.
            false
        }
    }


    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)
        updateAllSummaries() // Ekrana dönüldüğünde özetleri yenile
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener)
    }

    // Maaş periyodu gibi diğer ayar değişikliklerini dinlemek için
    private val sharedPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PaydayRepository.KEY_PAY_PERIOD) {
            repository.savePayday(-1) // Eski maaş günü ayarını temizle
            updateAllSummaries()
        }
    }

    private fun updateAllSummaries() {
        updatePaydaySummary()
        updateCurrencySummary(PaydayRepository.KEY_SALARY, repository.getSalaryAmount())
        updateCurrencySummary(PaydayRepository.KEY_MONTHLY_SAVINGS, repository.getMonthlySavingsAmount())
    }

    private fun updatePaydaySummary() {
        val paydayPref = findPreference<Preference>(PaydayRepository.KEY_PAYDAY_VALUE)
        val period = repository.getPayPeriod()
        val dayValue = repository.getPaydayValue()
        val dateString = repository.getBiWeeklyRefDateString()

        paydayPref?.summary = when (period) {
            PayPeriod.MONTHLY -> if (dayValue != -1) "Her ayın $dayValue. günü" else getString(R.string.payday_not_set)
            PayPeriod.WEEKLY -> if (dayValue != -1) "Her hafta ${DayOfWeek.of(dayValue).getDisplayName(TextStyle.FULL, Locale("tr"))}" else getString(R.string.payday_not_set)
            PayPeriod.BI_WEEKLY -> if (dateString != null) LocalDate.parse(dateString).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale("tr"))) else getString(R.string.payday_not_set)
        }
    }

    private fun updateCurrencySummary(key: String, value: Long) {
        findPreference<EditTextPreference>(key)?.summary = formatToCurrency(value)
    }

    private fun formatToCurrency(value: Long): String {
        return if (value > 0) currencyFormatter.format(value) else getString(R.string.payday_not_set)
    }

    private fun showPaydaySelectionDialog() {
        when (repository.getPayPeriod()) {
            PayPeriod.MONTHLY -> {
                val days = (1..31).map { it.toString() }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_payday_dialog_title)
                    .setItems(days) { _, which ->
                        repository.savePayday(which + 1)
                        updatePaydaySummary()
                    }
                    .show()
            }
            PayPeriod.WEEKLY -> {
                val daysOfWeek = resources.getStringArray(R.array.days_of_week)
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_payday_dialog_title)
                    .setItems(daysOfWeek) { _, which ->
                        repository.savePayday(which + 1)
                        updatePaydaySummary()
                    }
                    .show()
            }
            PayPeriod.BI_WEEKLY -> {
                val c = Calendar.getInstance()
                repository.getBiWeeklyRefDateString()?.let {
                    val date = LocalDate.parse(it)
                    c.set(date.year, date.monthValue - 1, date.dayOfMonth)
                }
                DatePickerDialog(requireContext(), { _, year, month, day ->
                    repository.saveBiWeeklyReferenceDate(LocalDate.of(year, month + 1, day))
                    updatePaydaySummary()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
    }
}
