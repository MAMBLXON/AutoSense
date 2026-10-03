package com.mamblxon.autosense.ui.launcher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.ui.main.MainActivity
import com.mamblxon.autosense.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val hasProfile = withContext(Dispatchers.IO) {
                DefaultServiceRepository.get(applicationContext).countProfiles() > 0
            }

            val target = if (hasProfile) {
                Intent(this@LauncherActivity, MainActivity::class.java)
            } else {
                Intent(this@LauncherActivity, OnboardingActivity::class.java)
            }
            startActivity(target)
            finish()
        }
    }
}