package com.mamblxon.autosense.ui.main.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.databinding.ItemRecordBinding
import com.mamblxon.autosense.domain.model.ServiceRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServiceRecordAdapter : ListAdapter<ServiceRecord, ServiceRecordAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())

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