package com.codenzi.payday

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        setupPayPeriodPreference()
        setupPaydayPreference()
        // Maaş ve Tasarruf alanları için özel dinleyiciyi kuruyoruz
        setupCurrencyPreference(PaydayRepository.KEY_SALARY.name)
        setupCurrencyPreference(PaydayRepository.KEY_MONTHLY_SAVINGS.name)
    }

    private fun setupPayPeriodPreference() {
        val payPeriodPref = findPreference<ListPreference>(PaydayRepository.KEY_PAY_PERIOD.name)
        payPeriodPref?.setOnPreferenceChangeListener { _, _ ->
            lifecycleScope.launch {
                repository.savePayday(-1)
            }
            true
        }
    }

    private fun setupPaydayPreference() {
        findPreference<Preference>(PaydayRepository.KEY_PAYDAY_VALUE.name)?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                showPaydaySelectionDialog()
            }
            true
        }
    }

    // *** HATAYI DÜZELTEN KISIM BURASI ***
    private fun setupCurrencyPreference(key: String) {
        val preference = findPreference<EditTextPreference>(key)
        preference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        preference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
            // Gelen yeni değeri (String) Long'a çevir
            val valueAsLong = (newValue as? String)?.toLongOrNull() ?: 0L

            // Coroutine içinde ilgili repository fonksiyonunu çağır
            lifecycleScope.launch {
                if (key == PaydayRepository.KEY_SALARY.name) {
                    repository.saveSalary(valueAsLong)
                } else if (key == PaydayRepository.KEY_MONTHLY_SAVINGS.name) {
                    repository.saveMonthlySavings(valueAsLong)
                }
                // Kaydettikten sonra özeti güncelle
                pref.summary = formatToCurrency(valueAsLong)
            }

            // 'false' döndürerek EditTextPreference'ın değeri kendisinin
            // String olarak kaydetmesini engelliyoruz.
            false
        }
    }

    override fun onResume() {
        super.onResume()
        updateAllSummaries()
    }

    private fun updateAllSummaries() {
        lifecycleScope.launch {
            updatePaydaySummary()
            updateCurrencySummary(PaydayRepository.KEY_SALARY.name, repository.getSalaryAmount().first())
            updateCurrencySummary(PaydayRepository.KEY_MONTHLY_SAVINGS.name, repository.getMonthlySavingsAmount().first())
        }
    }

    private suspend fun updatePaydaySummary() {
        val paydayPref = findPreference<Preference>(PaydayRepository.KEY_PAYDAY_VALUE.name)
        val period = repository.getPayPeriod().first()
        val dayValue = repository.getPaydayValue().first()
        val dateString = repository.getBiWeeklyRefDateString().first()

        paydayPref?.summary = when (period) {
            PayPeriod.MONTHLY -> if (dayValue != -1) "Her ayın $dayValue. günü" else getString(R.string.payday_not_set)
            PayPeriod.WEEKLY -> if (dayValue != -1) "Her hafta ${DayOfWeek.of(dayValue).getDisplayName(TextStyle.FULL, Locale("tr"))}" else getString(R.string.payday_not_set)
            PayPeriod.BI_WEEKLY -> if (!dateString.isNullOrEmpty()) LocalDate.parse(dateString).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale("tr"))) else getString(R.string.payday_not_set)
        }
    }

    private fun updateCurrencySummary(key: String, value: Long) {
        val pref = findPreference<EditTextPreference>(key)
        pref?.summary = formatToCurrency(value)
        pref?.text = if (value > 0) value.toString() else null
    }

    private fun formatToCurrency(value: Long): String {
        return if (value > 0) currencyFormatter.format(value) else getString(R.string.payday_not_set)
    }

    private suspend fun showPaydaySelectionDialog() {
        when (repository.getPayPeriod().first()) {
            PayPeriod.MONTHLY -> {
                val days = (1..31).map { it.toString() }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_payday_dialog_title)
                    .setItems(days) { _, which ->
                        lifecycleScope.launch { repository.savePayday(which + 1); updatePaydaySummary() }
                    }
                    .show()
            }
            PayPeriod.WEEKLY -> {
                val daysOfWeek = resources.getStringArray(R.array.days_of_week)
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_payday_dialog_title)
                    .setItems(daysOfWeek) { _, which ->
                        lifecycleScope.launch { repository.savePayday(which + 1); updatePaydaySummary() }
                    }
                    .show()
            }
            PayPeriod.BI_WEEKLY -> {
                val today = Calendar.getInstance()
                repository.getBiWeeklyRefDateString().first()?.let {
                    val date = LocalDate.parse(it)
                    today.set(date.year, date.monthValue - 1, date.dayOfMonth)
                }
                DatePickerDialog(requireContext(), { _, year, month, day ->
                    lifecycleScope.launch {
                        repository.saveBiWeeklyReferenceDate(LocalDate.of(year, month + 1, day))
                        updatePaydaySummary()
                    }
                }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
    }
}