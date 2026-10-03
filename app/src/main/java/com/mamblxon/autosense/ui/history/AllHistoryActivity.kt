package com.mamblxon.autosense.ui.history

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mamblxon.autosense.databinding.ActivityAllHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class AllHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllHistoryBinding
    private val viewModel: AllHistoryViewModel by viewModels()
    private val adapter = AllHistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        val fmt = NumberFormat.getIntegerInstance(Locale("ru", "RU"))

        lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                adapter.submitList(st.records)

                if (st.records.isEmpty()) {
                    binding.empty.visibility = View.VISIBLE
                    binding.recycler.visibility = View.GONE
                    binding.summaryCard.visibility = View.GONE
                } else {
                    binding.empty.visibility = View.GONE
                    binding.recycler.visibility = View.VISIBLE
                    binding.summaryCard.visibility = View.VISIBLE
                    binding.tvTotalRecords.text = "Всего записей: ${st.count}"
                    binding.tvTotalCost.text = if (st.totalCost > 0)
                        "Потрачено: ${fmt.format(st.totalCost)} ₽"
                    else "Стоимость не указана"
                }
            }
        }
    }
}