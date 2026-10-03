package com.mamblxon.autosense.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_items",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["car"])
    ]
)
data class ServiceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long = 0L,   // ← это поле обязательно
    val car: String,
    val name: String,
    val category: String = "Прочее",
    val intervalKm: Int,
    val intervalMonths: Int,
    val lastServiceKm: Int,
    val lastServiceDateMillis: Long,
    val currentKm: Int = 0,
    val urgency: Int = URGENCY_GREEN,
    val snoozedUntilMillis: Long = 0L,
    val fluidSpec: String = "",
    val volume: String = "",
    val notes: String = ""
) {
    companion object {
        const val URGENCY_GREEN = 0
        const val URGENCY_YELLOW = 1
        const val URGENCY_RED = 2
    }
}