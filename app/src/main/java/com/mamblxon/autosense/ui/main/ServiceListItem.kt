package com.mamblxon.autosense.ui.main

import com.mamblxon.autosense.domain.model.ServiceItem

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

    data class Row(val item: ServiceItem) : ServiceListItem {
        override val id: Long get() = item.id
    }

    companion object {
        fun headerId(category: String): Long = category.hashCode().toLong()
    }
}