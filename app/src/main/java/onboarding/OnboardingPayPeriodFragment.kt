package com.codenzi.payday.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.codenzi.payday.PayPeriod
import com.codenzi.payday.PaydayViewModel
import com.codenzi.payday.R
import com.codenzi.payday.databinding.FragmentOnboardingPayPeriodBinding

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // DÜZELTME: Deprecated listener modern versiyonu ile değiştirildi ve
        // kullanılmayan 'group' parametresi '_' olarak adlandırıldı.
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedPeriod = when (checkedIds.first()) {
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