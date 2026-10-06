package com.mamblxon.autosense.ui.main.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.databinding.ItemRecordBinding
import com.mamblxon.autosense.domain.model.ServiceRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Список истории ТО на экране детали.
 * Долгое нажатие на запись открывает диалог редактирования (onEditRequest).
 */
class ServiceRecordAdapter(
    private val onEditRequest: (ServiceRecord) -> Unit = {}
) : ListAdapter<ServiceRecord, ServiceRecordAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
    private val numberFmt = java.text.NumberFormat.getNumberInstance(Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemRecordBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ServiceRecord) {
            b.tvDate.text = fmt.format(Date(item.dateMillis))
            b.tvKm.text = "Пробег: ${item.km} км"

            if (item.cost > 0) {
                b.tvCost.visibility = View.VISIBLE
                b.tvCost.text = "${numberFmt.format(item.cost)} ₽"
            } else {
                b.tvCost.visibility = View.GONE
            }

            if (item.note.isNotBlank()) {
                b.tvNote.visibility = View.VISIBLE
                b.tvNote.text = item.note
            } else {
                b.tvNote.visibility = View.GONE
            }

            b.root.setOnLongClickListener {
                onEditRequest(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ServiceRecord>() {
            override fun areItemsTheSame(oldItem: ServiceRecord, newItem: ServiceRecord) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ServiceRecord, newItem: ServiceRecord) =
                oldItem == newItem
        }
    }
}
