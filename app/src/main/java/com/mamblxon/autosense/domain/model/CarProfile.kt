package com.mamblxon.autosense.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "car_profiles",
    indices = [Index(value = ["catalogId"])]   // без unique
)
data class CarProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val catalogId: String,
    val year: Int,
    val engineCode: String,
    val transmission: String,
    val vin: String = "",
    val currentKm: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)