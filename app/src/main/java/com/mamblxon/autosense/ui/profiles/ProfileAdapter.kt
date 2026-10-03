package com.mamblxon.autosense.ui.profiles

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.databinding.ItemProfileBinding

class ProfileAdapter(
    private val onClick: (ProfileDisplay) -> Unit,
    private val onLongClick: (ProfileDisplay) -> Unit
) : ListAdapter<ProfileDisplay, ProfileAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProfileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemProfileBinding) : RecyclerView.ViewHolder(b.root) {
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

        fun bind(d: ProfileDisplay) {
            b.tvName.text = d.displayName
            b.tvSubtitle.text = d.subtitle
            b.tvCurrentBadge.visibility =
                if (d.isCurrent) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProfileDisplay>() {
            override fun areItemsTheSame(a: ProfileDisplay, b: ProfileDisplay) =
                a.profile.id == b.profile.id
            override fun areContentsTheSame(a: ProfileDisplay, b: ProfileDisplay) = a == b
        }
    }
}