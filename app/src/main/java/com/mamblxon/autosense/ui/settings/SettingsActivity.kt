package com.mamblxon.autosense.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mamblxon.autosense.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.settingsContainer.id, SettingsFragment())
                .commit()
        }
    }
}