package com.mamblxon.autosense.ui.onboarding

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.databinding.ItemServiceHistoryBinding

class ServiceHistoryAdapter(
    private val onChanged: (Int, Boolean) -> Unit,
    private val onKm: (Int, Int) -> Unit
) : ListAdapter<ServiceRow, ServiceHistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemServiceHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemServiceHistoryBinding) : RecyclerView.ViewHolder(b.root) {

        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val km = s?.toString()?.trim()?.toIntOrNull() ?: 0
                onKm(pos, km)
            }
        }

        init {
            b.etKm.addTextChangedListener(watcher)

            b.switchChanged.setOnCheckedChangeListener { _, checked ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                onChanged(pos, checked)
            }
        }

        fun bind(row: ServiceRow) {
            b.tvName.text = row.spec.name
            b.tvCategory.text = buildString {
                append(row.spec.category)
                append(" · каждые ")
                append(row.spec.intervalKm)
                append(" км / ")
                append(row.spec.intervalMonths)
                append(" мес")
            }

            b.switchChanged.setOnCheckedChangeListener(null)
            b.switchChanged.isChecked = row.state.changed
            b.switchChanged.setOnCheckedChangeListener { _, checked ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                onChanged(pos, checked)
            }

            b.tilKm.isVisible = row.state.changed

            val wanted = row.state.lastKm.toString()
            b.etKm.removeTextChangedListener(watcher)
            if (b.etKm.text?.toString() != wanted) {
                b.etKm.setText(wanted)
                b.etKm.setSelection(wanted.length)
            }
            b.etKm.addTextChangedListener(watcher)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ServiceRow>() {
            override fun areItemsTheSame(a: ServiceRow, b: ServiceRow) =
                a.spec.name == b.spec.name && a.spec.category == b.spec.category

            override fun areContentsTheSame(a: ServiceRow, b: ServiceRow) = a == b
        }
    }
}
