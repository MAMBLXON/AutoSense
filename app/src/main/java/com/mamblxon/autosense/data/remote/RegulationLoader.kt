package com.mamblxon.autosense.data.remote

import android.content.Context
import kotlinx.serialization.json.Json

object RegulationsLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadRegulations(context: Context): List<CarRegulation> {
        val raw = context.assets
            .open("car_regulations.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val parsed = json.decodeFromString<RegulationsFile>(raw)
        return parsed.regulations
    }
}