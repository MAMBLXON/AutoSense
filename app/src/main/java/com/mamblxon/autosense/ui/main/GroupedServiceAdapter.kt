package com.mamblxon.autosense.ui.main

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ItemServiceBinding
import com.mamblxon.autosense.databinding.ItemServiceHeaderBinding
import com.mamblxon.autosense.domain.model.ServiceItem
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Свайп-действия для строк списка:
 *  • свайп влево  → удаление (красный фон + иконка корзины);
 *  • свайп вправо → редактирование (синий фон + иконка карандаша).
 */
interface SwipeActionHandler {
    fun onSwipeDelete(item: ServiceItem)
    fun onSwipeEdit(item: ServiceItem)
}

class GroupedServiceAdapter(
    private val onClick: (ServiceItem) -> Unit,
    private val onLongClick: (ServiceItem) -> Unit,
    private val swipeHandler: SwipeActionHandler? = null
) : ListAdapter<ServiceListItem, RecyclerView.ViewHolder>(DIFF) {

    private var touchHelper: ItemTouchHelper? = null

    /** Позиция строки в списке или -1, если holder — заголовок. */
    private fun rowItemPosition(holder: RecyclerView.ViewHolder): Int {
        val pos = holder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return -1
        return if (getItem(pos) is ServiceListItem.Row) pos else -1
    }

    /** Привязывает ItemTouchHelper к RecyclerView (свайпы работают только на строках). */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        if (touchHelper != null) return
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                0

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun isValidDropTarget(target: RecyclerView.ViewHolder?) =
                target != null && rowItemPosition(target) >= 0

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = rowItemPosition(viewHolder)
                if (pos < 0) return
                val item = (getItem(pos) as ServiceListItem.Row).item
                when (direction) {
                    ItemTouchHelper.LEFT -> swipeHandler?.onSwipeDelete(item)
                    ItemTouchHelper.RIGHT -> swipeHandler?.onSwipeEdit(item)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Возвращаем карточку в исходное состояние после отменённого/завершённого свайпа.
                viewHolder.itemView.translationX = 0f
                resetSwipeVisual(viewHolder.itemView)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val swiping = actionState == ItemTouchHelper.ACTION_STATE_SWIPE &&
                        rowItemPosition(viewHolder) >= 0
                if (!swiping) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    return
                }
                drawSwipeFeedback(c, viewHolder.itemView, dX)
                // Элемент немного уменьшается по мере свайпа (эффект как в Gmail).
                applySwipeScale(viewHolder.itemView, dX)
                // Рисуем сам сдвиг вручную, поэтому базовую реализацию не вызываем.
            }
        }
        touchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recyclerView) }
    }

    // ---------- Визуальная обратная связь свайпа ----------

    private val swipePaint = Paint()
    private val swipeIconRect = RectF()

    private fun drawSwipeFeedback(c: Canvas, itemView: android.view.View, dX: Float) {
        val ctx = itemView.context
        val left = itemView.left.toFloat()
        val right = itemView.right.toFloat()
        val top = itemView.top.toFloat()
        val bottom = itemView.bottom.toFloat()
        val radius = 16f * ctx.resources.displayMetrics.density

        val isDelete = dX < 0f
        if (isDelete && swipeHandler == null) return
        if (!isDelete && swipeHandler == null) return

        val colorRes = if (isDelete) R.color.swipe_delete else R.color.swipe_edit
        val iconRes = if (isDelete) R.drawable.ic_swipe_delete else R.drawable.ic_swipe_edit

        swipePaint.color = ContextCompat.getColor(ctx, colorRes)

        // Фон действия рисуем под карточкой со стороны, куда тянем элемент.
        val revealedWidth = abs(dX)
        if (revealedWidth <= 0f) return
        val backgroundRect = if (isDelete) {
            RectF(right - revealedWidth, top, right, bottom)
        } else {
            RectF(left, top, left + revealedWidth, bottom)
        }
        c.drawRoundRect(backgroundRect, radius, radius, swipePaint)

        // Иконка появляется плавно: прозрачность растёт по мере свайпа.
        val threshold = revealedWidth * 0.35f
        val alpha = ((revealedWidth - threshold) / max(1f, revealedWidth)).coerceIn(0f, 1f)
        if (alpha <= 0.05f) return

        val icon = ContextCompat.getDrawable(ctx, iconRes) ?: return
        val size = (24f * ctx.resources.displayMetrics.density).toInt()
        val cx = if (isDelete) right - revealedWidth / 2f else left + revealedWidth / 2f
        val cy = (top + bottom) / 2f
        swipeIconRect.set(
            (cx - size / 2f), (cy - size / 2f),
            (cx + size / 2f), (cy + size / 2f)
        )
        icon.bounds = swipeIconRect
        icon.alpha = (alpha * 255).roundToInt()
        icon.draw(c)
    }

    private fun applySwipeScale(itemView: android.view.View, dX: Float) {
        val fraction = abs(dX) / max(1f, itemView.width.toFloat())
        val scale = 1f - 0.08f * fraction.coerceIn(0f, 1f)
        itemView.scaleX = scale
        itemView.scaleY = scale
    }

    private fun resetSwipeVisual(itemView: android.view.View) {
        itemView.scaleX = 1f
        itemView.scaleY = 1f
        itemView.rotation = 0f
    }

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