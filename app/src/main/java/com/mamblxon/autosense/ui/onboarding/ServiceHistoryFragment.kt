package com.mamblxon.autosense.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mamblxon.autosense.databinding.FragmentServiceHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ServiceHistoryFragment : Fragment() {

    private var _binding: FragmentServiceHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServiceHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ServiceHistoryAdapter(
            onChanged = { index, changed -> viewModel.setServiceChanged(index, changed) },
            onKm = { index, km -> viewModel.setServiceKm(index, km) }
        )
        binding.recycler.adapter = adapter

        binding.btnMarkAll.setOnClickListener { viewModel.markAllChanged(true) }
        binding.btnClearAll.setOnClickListener { viewModel.markAllChanged(false) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                val model = st.selection.model ?: return@collectLatest
                val rows = model.serviceItems.mapIndexed { index, spec ->
                    val entry = st.selection.serviceEntries.getOrNull(index)
                        ?: ServiceEntryState(index = index)
                    ServiceRow(spec = spec, state = entry)
                }
                adapter.submitList(rows)

                binding.tvSummary.text = if (st.selection.changedCount == 0) {
                    "Ничего не отмечено — все работы будут считаться «неизвестно когда менялись»"
                } else {
                    "Отмечено: ${st.selection.changedCount} из ${rows.size}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class ServiceRow(
    val spec: com.mamblxon.autosense.data.remote.CarServiceSpec,
    val state: ServiceEntryState
)