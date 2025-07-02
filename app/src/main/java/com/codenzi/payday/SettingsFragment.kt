package com.codenzi.payday

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.*

@Suppress("DEPRECATION")
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var repository: PaydayRepository
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
    private val viewModel: PaydayViewModel by activityViewModels()

    private lateinit var googleSignInClient: GoogleSignInClient
    private var accountCategory: PreferenceCategory? = null
    private var googleAccountPreference: Preference? = null
    private var deleteAccountPreference: Preference? = null


    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                Toast.makeText(requireContext(), "Hoş geldin, ${account.displayName}", Toast.LENGTH_SHORT).show()
                updateAccountSection(account)
                requireActivity().recreate()
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Giriş yapılamadı.", Toast.LENGTH_SHORT).show()
                updateAccountSection(null)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        repository = PaydayRepository(requireContext())

        setupGoogleClient()
        setupAccountPreferences()
        setupObservers()

        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            val theme = newValue as String
            lifecycleScope.launch {
                repository.saveTheme(theme)
                when (theme) {
                    "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
            true
        }

        setupPayPeriodPreference()
        setupPaydayPreference()
        setupCurrencyPreference("salary")
        setupCurrencyPreference("monthly_savings")
        setupAutoBackupPreference()
        setupAutoSavingPreference()
    }

    private fun setupObservers() {
        viewModel.accountDeletionResult.observe(this) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    googleSignInClient.signOut().addOnCompleteListener {
                        Toast.makeText(requireContext(), "Tüm verileriniz silindi ve çıkış yapıldı.", Toast.LENGTH_LONG).show()
                        val intent = Intent(requireActivity(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    }
                } else {
                    Toast.makeText(requireContext(), "Veri silinirken bir hata oluştu. Lütfen tekrar deneyin.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
        updateAccountSection(GoogleSignIn.getLastSignedInAccount(requireContext()))
    }

    private fun setupAutoSavingPreference() {
        val autoSavingPref = findPreference<SwitchPreferenceCompat>("auto_saving_enabled")
        lifecycleScope.launch {
            autoSavingPref?.isChecked = repository.isAutoSavingEnabled().first()
        }
        autoSavingPref?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                repository.saveAutoSavingEnabled(newValue as Boolean)
            }
            true
        }
    }

    private fun setupGoogleClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }

    private fun setupAccountPreferences() {
        accountCategory = findPreference("account_category")
        googleAccountPreference = findPreference("google_account")
        deleteAccountPreference = findPreference("delete_account")

        googleAccountPreference?.setOnPreferenceClickListener {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (account == null) {
                val signInIntent: Intent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            } else {
                showSignOutDialog()
            }
            true
        }

        deleteAccountPreference?.setOnPreferenceClickListener {
            showDeleteAccountConfirmationDialog()
            true
        }
    }

    private fun updateAccountSection(account: GoogleSignInAccount?) {
        if (account != null) {
            accountCategory?.title = getString(R.string.profile_title)
            googleAccountPreference?.title = getString(R.string.google_sign_out_title)
            googleAccountPreference?.summary = account.email
            deleteAccountPreference?.isVisible = true
        } else {
            accountCategory?.title = getString(R.string.account_category_title)
            googleAccountPreference?.title = getString(R.string.google_sign_in_title)
            googleAccountPreference?.summary = getString(R.string.google_sign_in_summary)
            deleteAccountPreference?.isVisible = false
        }
        updateAutoBackupSummary()
    }

    private fun showSignOutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.google_sign_out_title))
            .setMessage(getString(R.string.google_sign_out_confirmation_message))
            .setPositiveButton(R.string.action_sign_out) { _, _ ->
                viewModel.clearLocalData()
                googleSignInClient.signOut().addOnCompleteListener {
                    Toast.makeText(requireContext(), "Başarıyla çıkış yapıldı ve yerel veriler temizlendi.", Toast.LENGTH_LONG).show()
                    val intent = Intent(requireActivity(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_confirmation_message))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.delete_button_text) { _, _ ->
                viewModel.deleteAccount()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupAutoBackupPreference() {
        val autoBackupPref = findPreference<SwitchPreferenceCompat>("auto_backup_enabled")

        lifecycleScope.launch {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            val isEnabled = repository.isAutoBackupEnabled().first()
            autoBackupPref?.isEnabled = (account != null)
            autoBackupPref?.isChecked = isEnabled && (account != null)
        }

        autoBackupPref?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                repository.setAutoBackupEnabled(newValue as Boolean)
            }
            true
        }
    }

    private fun updateSummaries() {
        lifecycleScope.launch {
            val themePreference = findPreference<ListPreference>("theme")
            themePreference?.value = repository.getTheme().first()
            updatePaydaySummary()
            updateCurrencySummary("salary", repository.getSalaryAmount().first())
            updateCurrencySummary("monthly_savings", repository.getMonthlySavingsAmount().first())
            updateAutoBackupSummary()
        }
    }

    private fun updateAutoBackupSummary() {
        lifecycleScope.launch {
            val autoBackupPref = findPreference<SwitchPreferenceCompat>("auto_backup_enabled") ?: return@launch
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())

            if (account == null) {
                autoBackupPref.summary = getString(R.string.google_sign_in_summary)
                return@launch
            }

            val lastBackupTimestamp = repository.getLastBackupTimestamp().first()
            if (lastBackupTimestamp > 0) {
                autoBackupPref.summary = "Son yedekleme: ${formatTimestamp(lastBackupTimestamp)}"
            } else {
                autoBackupPref.summary = getString(R.string.auto_backup_summary)
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()

        return if (DateUtils.isToday(timestamp)) {
            "Bugün, ${android.text.format.DateFormat.getTimeFormat(requireContext()).format(Date(timestamp))}"
        } else {
            val dateFormat = android.text.format.DateFormat.getMediumDateFormat(requireContext())
            val timeFormat = android.text.format.DateFormat.getTimeFormat(requireContext())
            "${dateFormat.format(Date(timestamp))}, ${timeFormat.format(Date(timestamp))}"
        }
    }

    private fun setupPayPeriodPreference() {
        val payPeriodPref = findPreference<ListPreference>("pay_period")
        payPeriodPref?.setOnPreferenceChangeListener { _, _ ->
            lifecycleScope.launch {
                repository.savePayday(-1)
            }
            true
        }
    }

    private fun setupPaydayPreference() {
        findPreference<Preference>("payday")?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                showPaydaySelectionDialog()
            }
            true
        }
    }

    private fun setupCurrencyPreference(key: String) {
        val preference = findPreference<EditTextPreference>(key)
        preference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        preference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
            val valueAsLong = (newValue as? String)?.toLongOrNull() ?: 0L
            lifecycleScope.launch {
                if (key == "salary") {
                    repository.saveSalary(valueAsLong)
                } else if (key == "monthly_savings") {
                    repository.saveMonthlySavings(valueAsLong)
                }
                pref.summary = formatToCurrency(valueAsLong)
            }
            false
        }
    }

    private suspend fun updatePaydaySummary() {
        val paydayPref = findPreference<Preference>("payday")
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