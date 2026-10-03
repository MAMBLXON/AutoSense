package com.mamblxon.autosense.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mamblxon.autosense.data.remote.CarModel
import com.mamblxon.autosense.databinding.FragmentCarPickBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CarPickFragment : Fragment() {

    private var _binding: FragmentCarPickBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCarPickBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CarModelAdapter { model -> viewModel.selectModel(model) }
        binding.recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                val cars = st.catalog?.cars.orEmpty()
                adapter.submitList(cars)
                binding.progress.visibility = if (st.catalog == null) View.VISIBLE else View.GONE
                binding.tvEmpty.visibility =
                    if (st.catalog != null && cars.isEmpty()) View.VISIBLE else View.GONE

                val selectedId = st.selection.model?.id
                adapter.setSelectedId(selectedId)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}