package com.example.payday

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.payday.databinding.ActivityOnboardingBinding
import com.example.payday.onboarding.OnboardingPayPeriodFragment
import com.example.payday.onboarding.OnboardingPaydayFragment
import com.example.payday.onboarding.OnboardingSalaryFragment
import com.example.payday.onboarding.OnboardingSavingsFragment
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var repository: PaydayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PaydayRepository(this)

        if (repository.isOnboardingComplete()) {
            navigateToMain()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
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
        repository.setOnboardingComplete(true)
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private class OnboardingAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OnboardingPayPeriodFragment()
                1 -> OnboardingPaydayFragment()
                2 -> OnboardingSalaryFragment()
                else -> OnboardingSavingsFragment()
            }
        }
    }
}
