// bakituncher/payday/payday-45bf19400631339220219f4fc951dd9f8da20be8/app/src/main/java/com/example/payday/SettingsActivity.kt
package com.example.payday

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.payday.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PaydayRepository(this)

        setupToolbar()
        setupListeners()
        loadCurrentSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Ayarlar değiştiyse ana ekranı güncellemesi için sinyal gönder
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun loadCurrentSettings() {
        binding.weekendAdjustmentSwitch.isChecked = repository.isWeekendAdjustmentEnabled()
    }

    private fun setupListeners() {
        binding.weekendAdjustmentSwitch.setOnCheckedChangeListener { _, isChecked ->
            repository.saveWeekendAdjustmentSetting(isChecked)
            setResult(RESULT_OK) // Indicate that a setting was changed
            // No need to finish here, let the user continue changing settings
        }

        // Butonlara tıklandığında, MainActivity'e hangi dialogu açacağını söylüyoruz.
        binding.setPayPeriodButton.setOnClickListener { returnResult("show_pay_period_dialog") }
        binding.setPaydayButton.setOnClickListener { returnResult("show_payday_dialog") }
        binding.setSalaryButton.setOnClickListener { returnResult("show_salary_dialog") }
        binding.setMonthlySavingsButton.setOnClickListener { returnResult("show_monthly_savings_dialog") }
    }

    private fun returnResult(dialogKey: String) {
        val intent = Intent().apply { putExtra("dialog_to_show", dialogKey) }
        setResult(RESULT_OK, intent)
        finish() // Close SettingsActivity after sending the result
    }

    // Fiziksel geri tuşuna basıldığında da sonucu gönder
    override fun onBackPressed() {
        setResult(RESULT_OK)
        super.onBackPressed()
    }
}