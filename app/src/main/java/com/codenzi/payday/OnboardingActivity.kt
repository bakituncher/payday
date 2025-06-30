package com.codenzi.payday

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.codenzi.payday.databinding.ActivityOnboardingBinding
import com.codenzi.payday.onboarding.OnboardingPayPeriodFragment
import com.codenzi.payday.onboarding.OnboardingPaydayFragment
import com.codenzi.payday.onboarding.OnboardingSalaryFragment
import com.codenzi.payday.onboarding.OnboardingSavingsFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PaydayRepository(this)

        lifecycleScope.launch {
            if (repository.isOnboardingComplete().first()) {
                navigateToMain()
            } else {
                setupUI()
            }
        }
    }

    private fun setupUI() {
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false // Kullanıcının elle kaydırmasını engelle

        // Sayfa geçiş animasyonunu ayarla
        binding.viewPager.setPageTransformer(ZoomOutPageTransformer())

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // İlerleme çubuğunu (ProgressBar) güncelle
                binding.onboardingProgressBar.progress = position + 1

                // Butonların görünürlüğünü ve metnini ayarla
                binding.backButton.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
                if (position == adapter.itemCount - 1) {
                    binding.nextButton.text = getString(R.string.onboarding_finish)
                } else {
                    binding.nextButton.text = getString(R.string.onboarding_next)
                }
            }
        })

        binding.nextButton.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                onOnboardingFinished()
            }
        }

        binding.backButton.setOnClickListener {
            binding.viewPager.currentItem = binding.viewPager.currentItem - 1
        }
    }

    private fun onOnboardingFinished() {
        viewModel.triggerSetupCompleteAchievement()
        lifecycleScope.launch {
            repository.setOnboardingComplete(true)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ViewPager için Fragment Adapter'ı
    private class OnboardingAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4 // Kurulum ekranındaki toplam sayfa sayısı

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OnboardingPayPeriodFragment()
                1 -> OnboardingPaydayFragment()
                2 -> OnboardingSalaryFragment()
                else -> OnboardingSavingsFragment()
            }
        }
    }

    // Sayfalar arası geçişte küçülme ve solma efekti yaratan animasyon sınıfı
    private class ZoomOutPageTransformer : ViewPager2.PageTransformer {
        private val MIN_SCALE = 0.85f
        private val MIN_ALPHA = 0.5f

        override fun transformPage(view: View, position: Float) {
            view.apply {
                val pageWidth = width
                val pageHeight = height
                when {
                    position < -1 -> { // Ekranın solunda, görünmüyor
                        alpha = 0f
                    }
                    position <= 1 -> { // Geçiş animasyonunun uygulanacağı aralık [-1, 1]
                        val scaleFactor = MIN_SCALE.coerceAtLeast(1 - abs(position))
                        val vertMargin = pageHeight * (1 - scaleFactor) / 2
                        val horzMargin = pageWidth * (1 - scaleFactor) / 2
                        translationX = if (position < 0) {
                            horzMargin - vertMargin / 2
                        } else {
                            horzMargin + vertMargin / 2
                        }

                        // Sayfayı ölçeklendir
                        scaleX = scaleFactor
                        scaleY = scaleFactor

                        // Sayfanın şeffaflığını ayarla
                        alpha = (MIN_ALPHA + (((scaleFactor - MIN_SCALE) / (1 - MIN_SCALE)) * (1 - MIN_ALPHA)))
                    }
                    else -> { // Ekranın sağında, görünmüyor
                        alpha = 0f
                    }
                }
            }
        }
    }
}