package com.mamblxon.autosense.domain.usecase

import com.mamblxon.autosense.data.remote.CarRegulation
import com.mamblxon.autosense.data.remote.ServiceSpec
import com.mamblxon.autosense.domain.model.ServiceItem

/**
 * Общие тестовые данные. Не тест сам по себе — просто фабрики.
 * Лежат в тестовом source set, в проде недоступны.
 */
object Fixtures {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Фиксированное "сейчас" — 2026-01-01 00:00 UTC. Используем везде, где нужна дата. */
    const val NOW = 1_767_225_600_000L

    fun toyotaRegulation(): CarRegulation = CarRegulation(
        car = "Toyota",
        model = "Camry 2.5",
        services = listOf(
            ServiceSpec("Замена масла", intervalKm = 10_000, intervalMonths = 12),
            ServiceSpec("Воздушный фильтр", intervalKm = 30_000, intervalMonths = 24),
            ServiceSpec("Тормозная жидкость", intervalKm = 60_000, intervalMonths = 36)
        )
    )

    fun bmwRegulation(): CarRegulation = CarRegulation(
        car = "BMW",
        model = "320i",
        services = listOf(
            ServiceSpec("Замена масла", intervalKm = 15_000, intervalMonths = 12),
            ServiceSpec("Ремень ГРМ", intervalKm = 120_000, intervalMonths = 72)
        )
    )

    /**
     * Создаёт ServiceItem с разумными дефолтами.
     * В тестах переопределяем только то, что важно для конкретного случая.
     */
    fun item(
        id: Long = 1L,
        car: String = "Toyota",
        name: String = "Тестовая работа",
        intervalKm: Int = 10_000,
        intervalMonths: Int = 12,
        lastServiceKm: Int = 0,
        lastServiceDateMillis: Long = NOW,
        currentKm: Int = 0,
        urgency: Int = ServiceItem.URGENCY_GREEN
    ): ServiceItem = ServiceItem(
        id = id,
        car = car,
        name = name,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        lastServiceKm = lastServiceKm,
        lastServiceDateMillis = lastServiceDateMillis,
        currentKm = currentKm,
        urgency = urgency
    )
}