package com.mamblxon.autosense.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mamblxon.autosense.databinding.FragmentCarDetailsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CarDetailsFragment : Fragment() {

    private var _binding: FragmentCarDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCarDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                val model = st.selection.model ?: return@collectLatest
                val sel = st.selection

                // Годы
                if (binding.spinnerYear.adapter == null) {
                    val years = (2011..2025).toList().asReversed()
                    binding.spinnerYear.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        years
                    )
                }
                binding.spinnerYear.setSelection(
                    ((2011..2025).toList().asReversed().indexOf(sel.year)).coerceAtLeast(0),
                    false
                )

                // Двигатели
                val engineLabels = model.engines.map {
                    "${it.code} · ${it.displacement}L · ${it.power} л.с. · ${it.valves}V"
                }
                if (binding.spinnerEngine.adapter == null) {
                    binding.spinnerEngine.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        engineLabels
                    )
                }
                val engineIndex = model.engines.indexOfFirst { it.code == sel.engine?.code }
                    .coerceAtLeast(0)
                binding.spinnerEngine.setSelection(engineIndex, false)

                // КПП
                if (binding.spinnerTransmission.adapter == null) {
                    binding.spinnerTransmission.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        model.transmissions
                    )
                }
                val trIndex = model.transmissions.indexOf(sel.transmission).coerceAtLeast(0)
                binding.spinnerTransmission.setSelection(trIndex, false)

                // Пробег
                if (binding.etKm.text.isNullOrEmpty() && sel.km > 0) {
                    binding.etKm.setText(sel.km.toString())
                }

                // Подсказка двигателя
                val engineNote = sel.engine?.note.orEmpty()
                binding.tvEngineNote.text = engineNote
                binding.tvEngineNote.visibility =
                    if (engineNote.isBlank()) View.GONE else View.VISIBLE
            }
        }

        // Слушатели
        binding.spinnerYear.onItemSelectedListener = SimpleSpinnerListener { pos ->
            viewModel.setYear((2011..2025).toList().asReversed()[pos])
        }
        binding.spinnerEngine.onItemSelectedListener = SimpleSpinnerListener { pos ->
            val model = viewModel.state.value.selection.model ?: return@SimpleSpinnerListener
            model.engines.getOrNull(pos)?.let { viewModel.setEngine(it) }
        }
        binding.spinnerTransmission.onItemSelectedListener = SimpleSpinnerListener { pos ->
            val model = viewModel.state.value.selection.model ?: return@SimpleSpinnerListener
            model.transmissions.getOrNull(pos)?.let { viewModel.setTransmission(it) }
        }

        binding.etKm.doAfterTextChanged { text ->
            val km = text?.toString()?.toIntOrNull() ?: 0
            viewModel.setKm(km)
            binding.tilKm.error = if (km < 0 || km > 2_000_000) "Некорректный пробег" else null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class SimpleSpinnerListener(
    private val onSelected: (Int) -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) = onSelected(position)

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}

private inline fun android.widget.EditText.doAfterTextChanged(
    crossinline action: (android.text.Editable?) -> Unit
) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = action(s)
    })
}