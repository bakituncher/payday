package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            Toast.makeText(this, "Hoş geldin, ${account.displayName}", Toast.LENGTH_SHORT).show()

            showAutoBackupPrompt {
                checkForExistingBackup()
            }

        } catch (e: ApiException) {
            showLoading(false)
            Log.w(TAG, "Giriş hatası, kod: " + e.statusCode)
            Toast.makeText(this, "Giriş sırasında bir hata oluştu.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAutoBackupPrompt(onComplete: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Otomatik Yedekleme")
            .setMessage("Verilerinizin düzenli olarak Google Drive'a otomatik yedeklenmesini ister misiniz? Bu ayarı daha sonra Ayarlar menüsünden değiştirebilirsiniz.")
            .setCancelable(false)
            .setPositiveButton("Evet, Aç") { _, _ ->
                lifecycleScope.launch {
                    repository.setAutoBackupEnabled(true)
                    onComplete()
                }
            }
            .setNegativeButton("Hayır, Teşekkürler") { _, _ ->
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
            .setTitle("Yedek Bulundu")
            .setMessage("Google Drive'da bir yedeğiniz bulundu. Verileriniz geri yüklensin mi?")
            .setCancelable(false)
            .setPositiveButton("Evet, Geri Yükle") { _, _ -> restoreBackup() }
            .setNegativeButton("Hayır, Yeni Başla") { _, _ -> navigateToNextScreen() }
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
                    Toast.makeText(this@LoginActivity, "Verileriniz başarıyla geri yüklendi.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Yedek indirilemedi, yeni profil oluşturuluyor.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Geri yükleme sırasında kritik hata", e)
                Toast.makeText(this@LoginActivity, "Geri yükleme başarısız oldu.", Toast.LENGTH_LONG).show()
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
}
