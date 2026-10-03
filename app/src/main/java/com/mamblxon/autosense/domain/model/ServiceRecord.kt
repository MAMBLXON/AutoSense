package com.mamblxon.autosense.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_records",
    foreignKeys = [
        ForeignKey(
            entity = ServiceItem::class,
            parentColumns = ["id"],
            childColumns = ["serviceItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("serviceItemId"),
        Index("dateMillis")
    ]
)
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val serviceItemId: Long,
    val km: Int,
    val dateMillis: Long,
    val note: String = "",
    val cost: Int = 0
)