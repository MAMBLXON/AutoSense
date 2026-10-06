package com.mamblxon.autosense.ui.main

import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.ServiceUseCase

/**
 * Один элемент плоского списка: либо заголовок категории, либо сама работа.
 */
sealed interface ServiceListItem {

    /**
     * id — стабильный идентификатор элемента для DiffUtil/RecyclerView:
     * у строки это id записи, у заголовка — хэш категории (не меняется между эмиссиями).
     */
    val id: Long

    data class Header(
        val category: String,
        val count: Int,
        val hasUrgent: Boolean
    ) : ServiceListItem {
        override val id: Long get() = headerId(category)
    }

    /**
     * Строка списка. [remainingInfo] рассчитывается в MainViewModel (в памяти, без DAO)
     * и передаётся сюда вместе с item — адаптеру не нужно ничего вычислять самому.
     */
    data class Row(
        val item: ServiceItem,
        val remainingInfo: ServiceUseCase.RemainingInfo? = null
    ) : ServiceListItem {
        override val id: Long get() = item.id
    }

    companion object {
        fun headerId(category: String): Long = category.hashCode().toLong()
    }
}