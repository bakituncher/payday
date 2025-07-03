package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PaydayRepository(this)

        lifecycleScope.launch {
            // Tanıtım ekranının daha önce gösterilip gösterilmediğini kontrol et
            if (repository.hasIntroBeenShown().first()) {
                // Eğer daha önce gösterildiyse, doğrudan Google Giriş ekranına git
                navigateTo(LoginActivity::class.java)
            } else {
                // Eğer ilk açılışsa, tanıtım ekranına git
                navigateTo(IntroductoryActivity::class.java)
            }
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        // Bu aktiviteyi sonlandır ki geri tuşuna basınca buraya dönülmesin
        finish()
    }
}