package com.codenzi.payday

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

@Suppress("DEPRECATION")
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var repository: PaydayRepository
    private val currencyFormatter: NumberFormat by lazy { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    private val viewModel: PaydayViewModel by activityViewModels()

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleDriveManager: GoogleDriveManager
    private var accountCategory: PreferenceCategory? = null
    private var googleAccountPreference: Preference? = null
    private var deleteAccountPreference: Preference? = null

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                Toast.makeText(requireContext(), getString(R.string.welcome_back_toast, account.displayName), Toast.LENGTH_SHORT).show()
                updateAccountSection(account)
                checkForBackupAndRestore()
            }.addOnFailureListener {
                Toast.makeText(requireContext(), getString(R.string.sign_in_failed_toast), Toast.LENGTH_SHORT).show()
                updateAccountSection(null)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        repository = PaydayRepository(requireContext())
        googleDriveManager = GoogleDriveManager(requireContext())

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

        setupPaydayPreference()
        setupCurrencyPreference("salary")
        setupCurrencyPreference("monthly_savings")
        setupAutoBackupPreference()
        setupAutoSavingPreference()

        findPreference<Preference>("recurring_transactions")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), RecurringTransactionsActivity::class.java))
            true
        }
    }

    private fun setupObservers() {
        viewModel.accountDeletionResult.observe(this) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    googleSignInClient.signOut().addOnCompleteListener {
                        Toast.makeText(requireContext(), getString(R.string.all_data_deleted_and_signed_out), Toast.LENGTH_LONG).show()
                        val intent = Intent(requireActivity(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.account_delete_error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkForBackupAndRestore() {
        lifecycleScope.launch {
            if (googleDriveManager.isBackupAvailable()) {
                showRestoreDialog()
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_backup_found_toast), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRestoreDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_found_title)
            .setMessage(R.string.restore_confirmation_message_login)
            .setCancelable(false)
            .setPositiveButton(R.string.yes_restore) { _, _ -> restoreData() }
            .setNegativeButton(R.string.no_start_new) { _, _ -> /* Do nothing */ }
            .show()
    }

    private fun restoreData() {
        lifecycleScope.launch {
            try {
                val backupJson = googleDriveManager.downloadFileContent()
                if (backupJson != null) {
                    val backupData = Gson().fromJson(backupJson, BackupData::class.java)
                    repository.restoreDataFromBackup(backupData)
                    Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                    requireActivity().recreate()
                } else {
                    // DÜZELTME: Güvenli context kontrolü
                    val safeContext = context
                    if (safeContext != null) {
                        Toast.makeText(safeContext, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsRestore", "Error during restore", e)
                // DÜZELTME: Güvenli context kontrolü
                val safeContext = context
                if (safeContext != null) {
                    Toast.makeText(safeContext, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), getString(R.string.sign_out_success_local_data_cleared), Toast.LENGTH_LONG).show()
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
        MaterialAlertDialogBuilder(requireContext())
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
                autoBackupPref.summary = getString(R.string.last_backup_label) + formatTimestamp(lastBackupTimestamp)
            } else {
                autoBackupPref.summary = getString(R.string.auto_backup_summary)
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return if (DateUtils.isToday(timestamp)) {
            getString(R.string.today_label) + android.text.format.DateFormat.getTimeFormat(requireContext()).format(Date(timestamp))
        } else {
            val dateFormat = android.text.format.DateFormat.getMediumDateFormat(requireContext())
            val timeFormat = android.text.format.DateFormat.getTimeFormat(requireContext())
            "${dateFormat.format(Date(timestamp))}, ${timeFormat.format(Date(timestamp))}"
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
        val dayValue = repository.getPaydayValue().first()
        paydayPref?.summary = if (dayValue != -1) getString(R.string.payday_summary_monthly, dayValue) else getString(R.string.payday_not_set)
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
        val days = (1..31).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_payday_dialog_title)
            .setItems(days) { _, which ->
                lifecycleScope.launch { repository.savePayday(which + 1); updatePaydaySummary() }
            }
            .show()
    }
}