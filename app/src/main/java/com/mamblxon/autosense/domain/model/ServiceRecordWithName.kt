package com.mamblxon.autosense.domain.model

data class ServiceRecordWithName(
    val id: Long,
    val serviceItemId: Long,
    val km: Int,
    val dateMillis: Long,
    val note: String,
    val cost: Int,
    val serviceName: String,
    val serviceCategory: String
)