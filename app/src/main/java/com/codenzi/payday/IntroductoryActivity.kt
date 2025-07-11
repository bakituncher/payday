package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat // EKLENDİ
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.databinding.ActivityIntroductoryBinding
import kotlinx.coroutines.launch

class IntroductoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroductoryBinding
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Kenardan kenara görünüm için DÜZELTME
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityIntroductoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PaydayRepository(this)

        val slideInTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_top)
        val slideInBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)

        binding.introContainer.startAnimation(slideInTop)
        binding.privacyCard.startAnimation(slideInBottom)
        binding.startButton.startAnimation(slideInBottom)

        binding.startButton.setOnClickListener {
            lifecycleScope.launch {
                repository.setIntroShown(true)
                startActivity(Intent(this@IntroductoryActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}