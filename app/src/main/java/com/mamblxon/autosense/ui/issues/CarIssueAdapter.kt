package com.mamblxon.autosense.ui.issues

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.R
import com.mamblxon.autosense.data.remote.CarIssue
import com.mamblxon.autosense.databinding.ItemCarIssueBinding

class CarIssueAdapter : ListAdapter<CarIssue, CarIssueAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCarIssueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemCarIssueBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(issue: CarIssue) {
            val ctx = b.root.context

            b.tvTitle.text = issue.title
            b.tvDescription.text = issue.description
            b.tvRecommendation.text = issue.recommendation

            // Бейдж и цвет обводки
            val (label, colorRes) = when (issue.severity) {
                "critical" -> "КРИТИЧНО" to R.color.urgency_red
                "warning" -> "ВАЖНО" to R.color.urgency_yellow
                else -> "ИНФО" to R.color.primary
            }
            val color = ContextCompat.getColor(ctx, colorRes)

            b.badge.text = label
            b.badge.setTextColor(color)

            // Полупрозрачный фон бейджа
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = ctx.resources.displayMetrics.density * 6
                setColor((color and 0x00FFFFFF) or 0x1F000000)
            }
            b.badge.background = bg

            // Цветная полоска слева
            b.severityStripe.setBackgroundColor(color)

            // Пробег, если указан
            if (issue.atKm > 0) {
                b.tvAtKm.visibility = View.VISIBLE
                b.tvAtKm.text = "Обычно проявляется около ${issue.atKm} км"
            } else {
                b.tvAtKm.visibility = View.GONE
            }

            // Заголовок "Рекомендация"
            b.tvRecommendationLabel.visibility = View.VISIBLE
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CarIssue>() {
            override fun areItemsTheSame(a: CarIssue, b: CarIssue) =
                a.title == b.title

            override fun areContentsTheSame(a: CarIssue, b: CarIssue) = a == b
        }
    }
}