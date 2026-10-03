package com.mamblxon.autosense.data.remote

import android.content.Context
import kotlinx.serialization.json.Json

object CarCatalogLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadCatalog(context: Context): CarCatalog {
        val raw = context.assets
            .open("car_catalog.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        return json.decodeFromString<CarCatalog>(raw)
    }

    fun findCar(catalog: CarCatalog, id: String): CarModel? =
        catalog.cars.firstOrNull { it.id == id }
}