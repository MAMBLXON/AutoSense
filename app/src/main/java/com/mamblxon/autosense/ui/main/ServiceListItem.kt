package com.mamblxon.autosense.ui.main

import com.mamblxon.autosense.domain.model.ServiceItem

/**
 * Один элемент плоского списка: либо заголовок категории, либо сама работа.
 */
sealed interface ServiceListItem {

    data class Header(
        val category: String,
        val count: Int,
        val hasUrgent: Boolean
    ) : ServiceListItem

    data class Row(val item: ServiceItem) : ServiceListItem
}