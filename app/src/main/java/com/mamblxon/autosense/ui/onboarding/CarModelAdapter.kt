package com.mamblxon.autosense.ui.onboarding

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.R
import com.mamblxon.autosense.data.remote.CarModel
import com.mamblxon.autosense.databinding.ItemCarModelBinding

class CarModelAdapter(
    private val onClick: (CarModel) -> Unit
) : ListAdapter<CarModel, CarModelAdapter.VH>(DIFF) {

    private var selectedId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCarModelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), getItem(position).id == selectedId)
    }

    fun setSelectedId(id: String?) {
        if (id == selectedId) return
        val old = selectedId
        selectedId = id
        currentList.forEachIndexed { index, model ->
            if (model.id == old || model.id == id) notifyItemChanged(index)
        }
    }

    inner class VH(private val b: ItemCarModelBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(model: CarModel, selected: Boolean) {
            val ctx = b.root.context

            b.tvName.text = model.displayName
            b.tvYears.text = model.years
            b.tvEngines.text = model.engines.joinToString(", ") {
                "${it.code} (${it.displacement}L ${it.power} л.с.)"
            }

            // Визуальное выделение выбранного — обводка 2dp цветом primary
            val strokePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                if (selected) 2f else 0f,
                ctx.resources.displayMetrics
            ).toInt()

            b.card.strokeWidth = strokePx
            b.card.strokeColor = ContextCompat.getColor(ctx, R.color.primary)

            b.card.setOnClickListener { onClick(model) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CarModel>() {
            override fun areItemsTheSame(a: CarModel, b: CarModel) = a.id == b.id
            override fun areContentsTheSame(a: CarModel, b: CarModel) = a == b
        }
    }
}