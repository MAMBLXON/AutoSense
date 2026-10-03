package com.mamblxon.autosense.ui.profiles

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mamblxon.autosense.databinding.ActivityProfilesBinding
import com.mamblxon.autosense.ui.common.AppDialogs
import com.mamblxon.autosense.ui.main.MainActivity
import com.mamblxon.autosense.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilesBinding
    private val viewModel: ProfilesViewModel by viewModels()

    private val adapter = ProfileAdapter(
        onClick = { display ->
            if (display.isCurrent) {
                finish()
                return@ProfileAdapter
            }
            viewModel.select(display.profile)
            restartMain()
        },
        onLongClick = { display -> confirmDelete(display) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                adapter.submitList(st.profiles)
                binding.empty.visibility = if (st.isEmpty) View.VISIBLE else View.GONE
                binding.recycler.visibility = if (st.isEmpty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun confirmDelete(display: ProfileDisplay) {
        AppDialogs.showConfirm(
            context = this,
            title = "Удалить профиль?",
            message = "\"${display.displayName}\" и вся история ТО по нему будут удалены.",
            positiveText = "Удалить",
            destructive = true,
            onConfirm = {
                viewModel.delete(display.profile) { needRestart ->
                    if (needRestart) restartMain()
                }
            }
        )
    }

    private fun restartMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}