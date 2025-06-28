package com.codenzi.payday.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.codenzi.payday.PayPeriod
import com.codenzi.payday.PaydayRepository
import com.codenzi.payday.PaydayViewModel
import com.codenzi.payday.R
import com.codenzi.payday.databinding.FragmentOnboardingPaydayBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        // UI kurulumunu bir coroutine içinde başlat
        viewLifecycleOwner.lifecycleScope.launch {
            setupUIForPayPeriod()
        }
    }

    // Fonksiyonu 'suspend' olarak işaretle
    private suspend fun setupUIForPayPeriod() {
        // repository'den gelen Flow'dan .first() ile ilk değeri al
        val payPeriod = repository.getPayPeriod().first()

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

    private fun setupWeeklyPicker() {
        val daysOfWeek = resources.getStringArray(R.array.days_of_week)
        binding.daysOfWeekChipGroup.removeAllViews()
        daysOfWeek.forEachIndexed { index, dayName ->
            val chip = Chip(requireContext()).apply {
                text = dayName
                isCheckable = true
                id = index + 1
            }
            binding.daysOfWeekChipGroup.addView(chip)
        }

        // Deprecated olan listener'ı yenisiyle değiştiriyoruz
        binding.daysOfWeekChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            // listede seçili ID varsa onu kullan
            if (checkedIds.isNotEmpty()) {
                viewModel.savePayday(checkedIds.first())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}