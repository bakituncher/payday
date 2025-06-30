// Konum: app/src/main/java/com/codenzi/payday/LoginActivity.kt

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

class LoginActivity : AppCompatActivity() {

    // DEĞİŞİKLİK: 'binding' değişkenini nullable yaparak ve geç başlatarak
    // ekranı göstermeden karar vermemizi sağlıyoruz.
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

        // --- YENİ EKLENEN ANA MANTIK ---
        // UI'ı (arayüzü) göstermeden önce bir kontrol yapıyoruz.
        lifecycleScope.launch {
            val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this@LoginActivity)
            // Kullanıcının "bir daha gösterme" tercihini repodan alıyoruz.
            val shouldShowLoginScreen = repository.shouldShowSignInPrompt().first()

            // Eğer kullanıcı daha önce giriş yapmışsa VEYA "bir daha gösterme"yi seçmişse,
            // bu ekranı hiç göstermeden doğrudan bir sonraki ekrana geç.
            if (lastSignedInAccount != null || !shouldShowLoginScreen) {
                navigateToNextScreen()
                return@launch // Bu coroutine'i sonlandır, aşağıdaki kodlar çalışmasın.
            }

            // Yukarıdaki koşul sağlanmazsa, demek ki giriş ekranını göstermeliyiz.
            // Bu yüzden arayüzü burada yüklüyoruz.
            setupUI()
        }
    }

    // YENİ EKLENEN METOT: Arayüzü kuran kodları ayrı bir metoda taşıdık.
    private fun setupUI() {
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        binding!!.signInButton.setOnClickListener {
            showLoading(true)
            val signInIntent = GoogleDriveManager.getSignInIntent(this)
            googleSignInLauncher.launch(signInIntent)
        }

        binding!!.skipButton.setOnClickListener {
            // Kullanıcı "Atla" butonuna bastığında, bu tercihini kaydediyoruz.
            lifecycleScope.launch {
                repository.setSignInPrompt(false) // Artık giriş ekranını gösterme.
                navigateToNextScreen()
            }
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "Giriş başarılı: ${account.displayName}")
            Toast.makeText(this, "Hoş geldin, ${account.displayName}", Toast.LENGTH_SHORT).show()
            checkForExistingBackup()
        } catch (e: ApiException) {
            showLoading(false)
            Log.w(TAG, "Giriş hatası, kod: " + e.statusCode)
            Toast.makeText(this, "Giriş sırasında bir hata oluştu.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkForExistingBackup() {
        showLoading(true)
        lifecycleScope.launch {
            if (googleDriveManager.isBackupAvailable()) {
                showLoading(false)
                showRestoreDialog()
            } else {
                Log.d(TAG, "Yedek bulunamadı, normal akışa devam ediliyor.")
                navigateToNextScreen()
            }
        }
    }

    private fun showRestoreDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Yedek Bulundu")
            .setMessage("Google Drive'da bir yedeğiniz bulundu. Verileriniz geri yüklensin mi?")
            .setCancelable(false)
            .setPositiveButton("Evet, Geri Yükle") { _, _ ->
                restoreBackup()
            }
            .setNegativeButton("Hayır, Yeni Başla") { _, _ ->
                navigateToNextScreen()
            }
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
            val targetActivity = if (isOnboardingComplete) {
                MainActivity::class.java
            } else {
                OnboardingActivity::class.java
            }
            startActivity(Intent(this@LoginActivity, targetActivity))
            finish() // Bu ekrana geri dönülmesini engelle
        }
    }

    private fun showLoading(isLoading: Boolean) {
        // Binding null olabileceği için `?` ile güvenli çağrı yapıyoruz.
        binding?.loadingProgressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding?.signInButton?.isEnabled = !isLoading
        binding?.skipButton?.isEnabled = !isLoading
    }
}