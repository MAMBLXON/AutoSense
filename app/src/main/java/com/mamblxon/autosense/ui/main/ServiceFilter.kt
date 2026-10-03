package com.mamblxon.autosense.ui.main

import com.mamblxon.autosense.domain.model.ServiceItem

enum class ServiceFilter(val label: String) {
    ALL("Все"),
    GREEN("ОК"),
    YELLOW("Скоро"),
    RED("Просрочено");

    fun matches(item: ServiceItem): Boolean = when (this) {
        ALL -> true
        GREEN -> item.urgency == ServiceItem.URGENCY_GREEN
        YELLOW -> item.urgency == ServiceItem.URGENCY_YELLOW
        RED -> item.urgency == ServiceItem.URGENCY_RED
    }
}