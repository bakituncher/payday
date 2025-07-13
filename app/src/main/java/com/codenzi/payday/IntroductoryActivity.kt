package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.databinding.ActivityIntroductoryBinding
import kotlinx.coroutines.launch

class IntroductoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroductoryBinding
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Kenardan kenara görünümü etkinleştir
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityIntroductoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PaydayRepository(this)

        // --- EN KRİTİK DÜZELTME BURADA ---
        // Bu kod, sistem çubuklarının (durum ve navigasyon çubukları)
        // içeriğin üzerine gelmesini engellemek için gerekli boşlukları (padding) dinamik olarak ayarlar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.introRootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Ana layout'a (ConstraintLayout) sol, üst, sağ ve alt boşlukları uygula
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            // Değişikliklerin diğer view'lara da iletilmesi için windowInsets'i geri döndür
            windowInsets
        }
        // --- DÜZELTME SONU ---

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