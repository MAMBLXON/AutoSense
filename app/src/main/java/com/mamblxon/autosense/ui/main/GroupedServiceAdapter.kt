package com.mamblxon.autosense.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ItemServiceBinding
import com.mamblxon.autosense.databinding.ItemServiceHeaderBinding
import com.mamblxon.autosense.domain.model.ServiceItem
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GroupedServiceAdapter(
    private val onClick: (ServiceItem) -> Unit,
    private val onLongClick: (ServiceItem) -> Unit
) : ListAdapter<ServiceListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is ServiceListItem.Header -> TYPE_HEADER
            is ServiceListItem.Row -> TYPE_ROW
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val b = ItemServiceHeaderBinding.inflate(inflater, parent, false)
                HeaderVH(b)
            }
            else -> {
                val b = ItemServiceBinding.inflate(inflater, parent, false)
                RowVH(b)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is HeaderVH && item is ServiceListItem.Header -> holder.bind(item)
            holder is RowVH && item is ServiceListItem.Row -> holder.bind(item.item)
        }
    }

    // ==================== Header ====================

    inner class HeaderVH(private val b: ItemServiceHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(header: ServiceListItem.Header) {
            b.tvCategory.text = header.category
            b.countBadge.text = header.count.toString()

            val ctx = b.root.context
            val accent = if (header.hasUrgent) {
                ContextCompat.getColor(ctx, R.color.urgency_yellow)
            } else {
                ContextCompat.getColor(ctx, R.color.primary)
            }
            b.countBadge.setTextColor(accent)

            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = ctx.resources.displayMetrics.density * 10
                setColor((accent and 0x00FFFFFF) or 0x1F000000)
            }
            b.countBadge.background = bg
        }
    }

    // ==================== Row ====================

    inner class RowVH(private val b: ItemServiceBinding) : RecyclerView.ViewHolder(b.root) {

        init {
            b.card.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val data = getItem(pos)
                    if (data is ServiceListItem.Row) onClick(data.item)
                }
            }
            b.card.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val data = getItem(pos)
                    if (data is ServiceListItem.Row) onLongClick(data.item)
                }
                true
            }
        }

        fun bind(item: ServiceItem) {
            b.name.text = item.name
            b.subtitle.text = buildString {
                append("каждые ").append(item.intervalKm).append(" км / ")
                append(item.intervalMonths).append(" мес")
                if (item.fluidSpec.isNotBlank()) {
                    append("\n").append(item.fluidSpec)
                }
            }

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
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1

        private val DIFF = object : DiffUtil.ItemCallback<ServiceListItem>() {
            override fun areItemsTheSame(a: ServiceListItem, b: ServiceListItem): Boolean =
                when {
                    a is ServiceListItem.Header && b is ServiceListItem.Header ->
                        a.category == b.category
                    a is ServiceListItem.Row && b is ServiceListItem.Row ->
                        a.item.id == b.item.id
                    else -> false
                }

            override fun areContentsTheSame(a: ServiceListItem, b: ServiceListItem): Boolean =
                a == b
        }
    }
}