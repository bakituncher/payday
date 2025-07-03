package com.codenzi.payday.onboarding

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.codenzi.payday.PaydayViewModel
import com.codenzi.payday.R
import com.codenzi.payday.databinding.FragmentOnboardingInputBinding

class OnboardingSalaryFragment : Fragment() {

    private var _binding: FragmentOnboardingInputBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PaydayViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.titleTextView.text = getString(R.string.onboarding_salary_title)
        binding.subtitleTextView.text = getString(R.string.onboarding_salary_subtitle)
        binding.inputLayout.hint = getString(R.string.onboarding_salary_hint)

        binding.inputEditText.addTextChangedListener { text ->
            val salary = text.toString().toLongOrNull() ?: 0L
            viewModel.saveSalary(salary)
        }

        // Klavye ve odaklanma sorununu çözen kod
        binding.inputEditText.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.inputEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}