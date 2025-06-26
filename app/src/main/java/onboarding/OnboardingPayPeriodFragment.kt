package com.example.payday.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.payday.PayPeriod
import com.example.payday.PaydayViewModel
import com.example.payday.R
import com.example.payday.databinding.FragmentOnboardingPayPeriodBinding

class OnboardingPayPeriodFragment : Fragment() {

    private var _binding: FragmentOnboardingPayPeriodBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PaydayViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPayPeriodBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chipGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId != View.NO_ID) {
                val selectedPeriod = when (checkedId) {
                    R.id.chip_monthly -> PayPeriod.MONTHLY
                    R.id.chip_bi_weekly -> PayPeriod.BI_WEEKLY
                    else -> PayPeriod.WEEKLY
                }
                viewModel.savePayPeriod(selectedPeriod)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
