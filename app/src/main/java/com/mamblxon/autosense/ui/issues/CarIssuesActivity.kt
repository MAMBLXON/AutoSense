package com.mamblxon.autosense.ui.issues

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ActivityCarIssuesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CarIssuesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarIssuesBinding
    private val viewModel: CarIssuesViewModel by viewModels()
    private val adapter = CarIssueAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarIssuesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipCritical -> IssueFilter.CRITICAL
                R.id.chipWarning -> IssueFilter.WARNING
                R.id.chipInfo -> IssueFilter.INFO
                else -> IssueFilter.ALL
            }
            viewModel.setFilter(filter)
        }

        lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                binding.toolbar.title = st.carDisplayName.ifBlank { "Особенности" }
                adapter.submitList(st.visibleIssues)

                binding.tvSubtitle.text = when {
                    st.allIssues.isEmpty() -> "Информация по этой модели пока не заполнена"
                    else -> "Найдено особенностей: ${st.allIssues.size}"
                }

                binding.empty.visibility =
                    if (st.visibleIssues.isEmpty()) android.view.View.VISIBLE
                    else android.view.View.GONE
                binding.recycler.visibility =
                    if (st.visibleIssues.isEmpty()) android.view.View.GONE
                    else android.view.View.VISIBLE
            }
        }
    }
}