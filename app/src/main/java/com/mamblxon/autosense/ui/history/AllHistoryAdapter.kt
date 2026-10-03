package com.mamblxon.autosense.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.databinding.ItemHistoryRecordBinding
import com.mamblxon.autosense.domain.model.ServiceRecordWithName
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AllHistoryAdapter : ListAdapter<ServiceRecordWithName, AllHistoryAdapter.VH>(DIFF) {

    private val dateFmt = SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault())
    private val numberFmt = NumberFormat.getIntegerInstance(Locale("ru", "RU"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemHistoryRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemHistoryRecordBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(r: ServiceRecordWithName) {
            b.tvName.text = r.serviceName
            b.tvDate.text = dateFmt.format(Date(r.dateMillis))
            b.tvKm.text = "Пробег: ${numberFmt.format(r.km)} км"
            b.tvCategory.text = r.serviceCategory.ifBlank { "ПРОЧЕЕ" }

            if (r.cost > 0) {
                b.tvCost.visibility = View.VISIBLE
                b.tvCost.text = "${numberFmt.format(r.cost)} ₽"
            } else {
                b.tvCost.visibility = View.GONE
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ServiceRecordWithName>() {
            override fun areItemsTheSame(a: ServiceRecordWithName, b: ServiceRecordWithName) =
                a.id == b.id

            override fun areContentsTheSame(a: ServiceRecordWithName, b: ServiceRecordWithName) =
                a == b
        }
    }
}