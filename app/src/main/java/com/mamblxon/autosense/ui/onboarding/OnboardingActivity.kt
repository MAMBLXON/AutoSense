package com.mamblxon.autosense.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mamblxon.autosense.databinding.ActivityOnboardingBinding
import com.mamblxon.autosense.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    private var currentStep: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        if (savedInstanceState == null) {
            showStep(1)
        } else {
            currentStep = savedInstanceState.getInt(STATE_STEP, 1)
            showStep(currentStep)
        }

        binding.btnBack.setOnClickListener {
            when (currentStep) {
                1 -> finish()
                else -> showStep(currentStep - 1)
            }
        }

        binding.btnNext.setOnClickListener {
            when (currentStep) {
                1 -> if (viewModel.state.value.selection.model != null) showStep(2)
                2 -> if (viewModel.state.value.selection.isStep2Complete) showStep(3)
                3 -> showStep(4)
                4 -> viewModel.confirm()
            }
        }

        // repeatOnLifecycle(STARTED): без утечки корутин при пересоздании Activity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { st ->
                    binding.progressIndicator.setProgressCompat(currentStep, true)
                    binding.tvStep.text = "Шаг $currentStep из 4"

                    binding.btnNext.isEnabled = when (currentStep) {
                        1 -> st.selection.model != null
                        2 -> st.selection.isStep2Complete
                        3 -> true
                        else -> !st.isSaving
                    }
                    binding.btnNext.text = when (currentStep) {
                        4 -> if (st.isSaving) "Сохраняем…" else "Создать профиль"
                        else -> "Далее"
                    }

                    if (st.done) {
                        startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, currentStep)
    }

    private fun showStep(step: Int) {
        currentStep = step
        val fragment: Fragment = when (step) {
            1 -> CarPickFragment()
            2 -> CarDetailsFragment()
            3 -> ServiceHistoryFragment()
            else -> CarConfirmFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
        binding.btnBack.isEnabled = true
        binding.btnBack.text = if (step == 1) "Отмена" else "Назад"
    }

    companion object {
        private const val STATE_STEP = "onboarding_step"
    }
}