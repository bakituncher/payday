package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private var binding: ActivityLoginBinding? = null
    private lateinit var googleDriveManager: GoogleDriveManager
    private lateinit var repository: PaydayRepository
    private val TAG = "LoginActivity"

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        } else {
            showLoading(false)
            Log.w(TAG, "Giriş akışı başarısız oldu veya iptal edildi.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleDriveManager = GoogleDriveManager(this)
        repository = PaydayRepository(this)

        lifecycleScope.launch {
            val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this@LoginActivity)
            val shouldShowOnStart = repository.shouldShowLoginOnStart().first()

            if (lastSignedInAccount != null || !shouldShowOnStart) {
                navigateToNextScreen()
                return@launch
            }

            setupUI()
        }
    }

    private fun setupUI() {
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        binding!!.signInButton.setOnClickListener {
            showLoading(true)
            val signInIntent = GoogleDriveManager.getSignInIntent(this)
            googleSignInLauncher.launch(signInIntent)
        }

        binding!!.skipButton.setOnClickListener {
            lifecycleScope.launch {
                repository.setShowLoginOnStart(false)
                navigateToNextScreen()
            }
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "Giriş başarılı: ${account.displayName}")
            showSnackbar(getString(R.string.welcome_message_user, account.displayName))
            showAutoBackupPrompt {
                checkForExistingBackup()
            }
        } catch (e: ApiException) {
            showLoading(false)
            Log.w(TAG, "Giriş hatası, kod: " + e.statusCode)
            val errorMessage = if (e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR) {
                getString(R.string.network_error_please_check_connection)
            } else {
                getString(R.string.login_error_occurred)
            }
            showSnackbar(errorMessage, isError = true)
        }
    }

    private fun showAutoBackupPrompt(onComplete: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_backup_title)
            .setMessage(R.string.auto_backup_prompt_message)
            .setCancelable(false)
            .setPositiveButton(R.string.yes_turn_on) { _, _ ->
                lifecycleScope.launch {
                    repository.setAutoBackupEnabled(true)
                    onComplete()
                }
            }
            .setNegativeButton(R.string.no_thanks) { _, _ ->
                lifecycleScope.launch {
                    repository.setAutoBackupEnabled(false)
                    onComplete()
                }
            }
            .show()
    }

    private fun checkForExistingBackup() {
        showLoading(true)
        lifecycleScope.launch {
            if (googleDriveManager.isBackupAvailable()) {
                showLoading(false)
                showRestoreDialog()
            } else {
                navigateToNextScreen()
            }
        }
    }

    private fun showRestoreDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_found_title)
            .setMessage(R.string.restore_confirmation_message_login)
            .setCancelable(false)
            .setPositiveButton(R.string.yes_restore) { _, _ -> restoreBackup() }
            .setNegativeButton(R.string.no_start_new) { _, _ -> navigateToNextScreen() }
            .show()
    }

    private fun restoreBackup() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val backupJson = googleDriveManager.downloadFileContent()
                if (backupJson != null) {
                    val backupData = Gson().fromJson(backupJson, BackupData::class.java)
                    repository.restoreDataFromBackup(backupData)
                    showSnackbar(getString(R.string.restore_success_login))
                } else {
                    showSnackbar(getString(R.string.backup_download_failed_creating_new_profile), isError = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Geri yükleme sırasında kritik hata", e)
                showSnackbar(getString(R.string.restore_failed_login), isError = true)
            } finally {
                navigateToNextScreen()
            }
        }
    }

    private fun navigateToNextScreen() {
        lifecycleScope.launch {
            val isOnboardingComplete = repository.isOnboardingComplete().first()
            val targetActivity = if (isOnboardingComplete) MainActivity::class.java else OnboardingActivity::class.java
            startActivity(Intent(this@LoginActivity, targetActivity))
            finish()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding?.loadingProgressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding?.signInButton?.isEnabled = !isLoading
        binding?.skipButton?.isEnabled = !isLoading
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        binding?.let {
            val snackbar = Snackbar.make(it.root, message, Snackbar.LENGTH_LONG)
            if (isError) {
                snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.red_500))
            }
            snackbar.show()
        }
    }
}
