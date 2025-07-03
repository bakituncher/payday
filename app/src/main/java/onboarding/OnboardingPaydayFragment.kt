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
        viewLifecycleOwner.lifecycleScope.launch {
            setupUIForPayPeriod()
        }
    }

    private suspend fun setupUIForPayPeriod() {
        // Artık sadece AYLIK olduğu için bu mantık basitleştirildi.
        binding.subtitleTextView.text = getString(R.string.onboarding_payday_subtitle_monthly)
        binding.viewFlipper.displayedChild = 0 // Sadece takvimi göster

        binding.calendarView.setOnDateChangeListener { _, _, _, dayOfMonth ->
            viewModel.savePayday(dayOfMonth)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}