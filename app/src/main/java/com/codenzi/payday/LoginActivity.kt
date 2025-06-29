// Konum: app/src/main/java/com/codenzi/payday/LoginActivity.kt

package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.codenzi.payday.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val TAG = "LoginActivity"

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        } else {
            Log.w(TAG, "Google giriş akışı iptal edildi veya başarısız oldu. Result code: ${result.resultCode}")
            Toast.makeText(this, "Giriş başarısız oldu.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kullanıcı daha önce giriş yapmışsa, bu ekranı hiç gösterme, doğrudan devam et.
        if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            Log.d(TAG, "Kullanıcı zaten giriş yapmış. Onboarding ekranına yönlendiriliyor.")
            navigateToNextScreen()
            return // onCreate'i erken bitirerek layout'un yüklenmesini engelle
        }

        // Kullanıcı giriş yapmamışsa, karşılama ekranını göster
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signInButton.setOnClickListener {
            val signInIntent = GoogleDriveManager.getSignInIntent(this)
            googleSignInLauncher.launch(signInIntent)
        }

        binding.skipButton.setOnClickListener {
            Log.d(TAG, "Kullanıcı girişi atladı. Onboarding ekranına yönlendiriliyor.")
            navigateToNextScreen()
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "Giriş başarılı. Kullanıcı: ${account.displayName}")
            Toast.makeText(this, "Hoş geldin, ${account.displayName}", Toast.LENGTH_SHORT).show()
            navigateToNextScreen()
        } catch (e: ApiException) {
            Log.w(TAG, "Giriş hatası, kod: " + e.statusCode)
            Toast.makeText(this, "Giriş sırasında bir hata oluştu. Lütfen tekrar deneyin.", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToNextScreen() {
        // Kullanıcıyı bir sonraki aşama olan Onboarding ekranına yönlendir
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish() // Bu ekrana geri dönülmesini engelle
    }
}