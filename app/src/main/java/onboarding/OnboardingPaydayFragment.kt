package com.example.payday.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.payday.PayPeriod
import com.example.payday.PaydayRepository
import com.example.payday.PaydayViewModel
import com.example.payday.R
import com.example.payday.databinding.FragmentOnboardingPaydayBinding
import com.google.android.material.chip.Chip
import java.time.LocalDate

class OnboardingPaydayFragment : Fragment() {

    private var _binding: FragmentOnboardingPaydayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PaydayViewModel by activityViewModels()
    private lateinit var repository: PaydayRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPaydayBinding.inflate(inflater, container, false)
        repository = PaydayRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUIForPayPeriod()
    }

    private fun setupUIForPayPeriod() {
        val payPeriod = repository.getPayPeriod()

        when (payPeriod) {
            PayPeriod.MONTHLY -> {
                binding.subtitleTextView.text = getString(R.string.onboarding_payday_subtitle_monthly)
                binding.viewFlipper.displayedChild = 0
                binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
                    viewModel.savePayday(dayOfMonth)
                }
            }
            PayPeriod.BI_WEEKLY -> {
                binding.subtitleTextView.text = getString(R.string.onboarding_payday_subtitle_bi_weekly)
                binding.viewFlipper.displayedChild = 0
                binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
                    viewModel.saveBiWeeklyReferenceDate(LocalDate.of(year, month + 1, dayOfMonth))
                }
            }
            PayPeriod.WEEKLY -> {
                binding.subtitleTextView.text = getString(R.string.onboarding_payday_subtitle_weekly)
                binding.viewFlipper.displayedChild = 1
                setupWeeklyPicker()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupWeeklyPicker() {
        val daysOfWeek = resources.getStringArray(R.array.days_of_week)
        binding.daysOfWeekChipGroup.removeAllViews()
        daysOfWeek.forEachIndexed { index, dayName ->
            val chip = Chip(requireContext()).apply {
                text = dayName
                isCheckable = true
                id = index
            }
            binding.daysOfWeekChipGroup.addView(chip)
        }

        binding.daysOfWeekChipGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != View.NO_ID) {
                viewModel.savePayday(checkedId + 1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}