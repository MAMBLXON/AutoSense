package com.mamblxon.autosense.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mamblxon.autosense.databinding.FragmentCarConfirmBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CarConfirmFragment : Fragment() {

    private var _binding: FragmentCarConfirmBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCarConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { st ->
                val sel = st.selection
                val model = sel.model
                binding.tvTitle.text = model?.displayName ?: "—"
                binding.tvDetails.text = buildString {
                    append("Год: ").append(sel.year).append("\n")
                    append("Двигатель: ").append(sel.engine?.code ?: "—")
                    sel.engine?.let {
                        append(" · ").append(it.displacement).append("L · ")
                        append(it.power).append(" л.с.")
                    }
                    append("\n")
                    append("КПП: ").append(sel.transmission ?: "—").append("\n")
                    append("Пробег: ").append(sel.km).append(" км\n")
                    append("Отмечено работ: ").append(sel.changedCount)
                    append(" из ").append(model?.serviceItems?.size ?: 0)
                }
                binding.tvError.text = st.error.orEmpty()
                binding.tvError.visibility =
                    if (st.error.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}