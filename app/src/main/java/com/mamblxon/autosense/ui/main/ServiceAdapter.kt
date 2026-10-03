package com.mamblxon.autosense.ui.main

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ItemServiceBinding
import com.mamblxon.autosense.domain.model.ServiceItem
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ServiceAdapter(
    private val onClick: (ServiceItem) -> Unit,
    private val onLongClick: (ServiceItem) -> Unit
) : ListAdapter<ServiceItem, ServiceAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemServiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemServiceBinding) : RecyclerView.ViewHolder(b.root) {

        init {
            b.card.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
            b.card.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onLongClick(getItem(pos))
                true
            }
        }

        fun bind(item: ServiceItem) {
            b.name.text = item.name
            b.subtitle.text = buildString {
                append(item.car)
                append(" · каждые ").append(item.intervalKm).append(" км / ")
                append(item.intervalMonths).append(" мес")
                if (item.currentKm > 0) {
                    append(" · тек. ").append(item.currentKm).append(" км")
                }
            }

            // Прогресс как процент выполнения интервала (max из км и месяцев)
            val kmProgress = if (item.intervalKm > 0)
                (item.currentKm - item.lastServiceKm).toDouble() / item.intervalKm
            else 0.0
            val daysPassed = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - item.lastServiceDateMillis
            )
            val monthsPassed = daysPassed / 30.0
            val monthProgress = if (item.intervalMonths > 0)
                monthsPassed / item.intervalMonths
            else 0.0
            val progress = maxOf(kmProgress, monthProgress)
            val percent = (progress * 100).roundToInt().coerceIn(0, 100)

            b.progress.setProgressCompat(percent, true)
            b.progressLabel.text = "$percent%"

            val colorRes = when (item.urgency) {
                ServiceItem.URGENCY_RED -> R.color.urgency_red
                ServiceItem.URGENCY_YELLOW -> R.color.urgency_yellow
                else -> R.color.urgency_green
            }
            val color = ContextCompat.getColor(b.root.context, colorRes)

            b.progress.setIndicatorColor(color)

            val (statusText, badgeRes) = when (item.urgency) {
                ServiceItem.URGENCY_RED -> "ПРОСРОЧЕНО" to R.drawable.badge_background_red
                ServiceItem.URGENCY_YELLOW -> "СКОРО" to R.drawable.badge_background_yellow
                else -> "ОК" to R.drawable.badge_background_green
            }
            b.statusBadge.text = statusText
            b.statusBadge.setTextColor(color)
            b.statusBadge.setBackgroundResource(badgeRes)
            b.statusBadge.isVisible = true
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ServiceItem>() {
            override fun areItemsTheSame(oldItem: ServiceItem, newItem: ServiceItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ServiceItem, newItem: ServiceItem) =
                oldItem == newItem
        }
    }
}