package com.codenzi.payday.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.codenzi.payday.PaydayViewModel
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

        // Sadece AYLIK seçeneği otomatik olarak seçiliyor
        // Hata veren satır düzeltildi. Fonksiyon artık parametresiz çağrılıyor.
        viewModel.savePayPeriod()

        // Diğer chipleri gizliyoruz
        binding.chipMonthly.isChecked = true
        binding.chipBiWeekly.visibility = View.GONE
        binding.chipWeekly.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}