package com.mamblxon.autosense.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RegulationsFile(
    val regulations: List<CarRegulation>
)

@Serializable
data class CarRegulation(
    val car: String,
    val model: String,
    val services: List<ServiceSpec>
)

@Serializable
data class ServiceSpec(
    val name: String,
    val intervalKm: Int,
    val intervalMonths: Int
)