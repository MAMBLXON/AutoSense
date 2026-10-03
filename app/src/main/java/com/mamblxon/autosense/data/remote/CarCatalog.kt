package com.mamblxon.autosense.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CarCatalog(
    val version: Int = 1,
    val cars: List<CarModel>
)

@Serializable
data class CarModel(
    val id: String,               // "lada_granta_2190"
    val brand: String,            // "LADA"
    val model: String,            // "Granta"
    val generation: String,       // "2190"
    val displayName: String,      // "LADA Granta (2190)"
    val years: String,            // "2011 – н.в."
    val engines: List<EngineSpec>,
    val transmissions: List<String>,
    val serviceItems: List<CarServiceSpec>,
    val issues: List<CarIssue>
)

@Serializable
data class EngineSpec(
    val code: String,             // "11183"
    val displacement: String,     // "1.6"
    val power: Int,               // 82
    val valves: Int,              // 8
    val fuel: String,             // "бензин"
    val note: String = ""         // "ремень не гнёт клапаны"
)

@Serializable
data class CarServiceSpec(
    val category: String,         // "Двигатель"
    val name: String,             // "Моторное масло и масляный фильтр"
    val intervalKm: Int,
    val intervalMonths: Int,
    val fluidSpec: String = "",   // "5W-30 / 5W-40, API SL/CF+"
    val volume: String = "",      // "3.5 л (8V) / 4.4 л (16V)"
    val notes: String = ""
)

@Serializable
data class CarIssue(
    /** "critical" | "warning" | "info" */
    val severity: String,
    val title: String,
    val description: String,
    val recommendation: String,
    /** При каком пробеге обычно проявляется */
    val atKm: Int = 0
)