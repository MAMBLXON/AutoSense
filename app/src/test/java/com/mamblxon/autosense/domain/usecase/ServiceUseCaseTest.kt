package com.mamblxon.autosense

import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.Fixtures
import com.mamblxon.autosense.domain.usecase.ServiceUseCase

class ServiceUseCaseTest {

    // Subject under test — то, что тестируем. Создаётся заново перед каждым тестом.
    private lateinit var useCase: ServiceUseCase

    @Before
    fun setUp() {
        useCase = ServiceUseCase()
    }

    // ========================================================================
    // computeUrgency
    // ========================================================================

    @Test
    fun `computeUrgency returns GREEN when progress is below threshold`() {
        // Arrange: 5000 км из 10000 = 50% от интервала
        val item = Fixtures.item(
            intervalKm = 10_000,
            lastServiceKm = 0,
            currentKm = 5_000
        )

        // Act
        val urgency = useCase.computeUrgency(item, nowMillis = Fixtures.NOW)

        // Assert
        assertEquals(ServiceItem.URGENCY_GREEN, urgency)
    }

    @Test
    fun `computeUrgency returns YELLOW when progress crosses default threshold`() {
        // 8500 из 10000 = 85%, выше 0.8
        val item = Fixtures.item(
            intervalKm = 10_000,
            lastServiceKm = 0,
            currentKm = 8_500
        )

        val urgency = useCase.computeUrgency(item, nowMillis = Fixtures.NOW)

        assertEquals(ServiceItem.URGENCY_YELLOW, urgency)
    }

    @Test
    fun `computeUrgency returns RED when progress reaches 100 percent`() {
        // 10000 из 10000 = 100%, ровно
        val item = Fixtures.item(
            intervalKm = 10_000,
            lastServiceKm = 0,
            currentKm = 10_000
        )

        val urgency = useCase.computeUrgency(item, nowMillis = Fixtures.NOW)

        assertEquals(ServiceItem.URGENCY_RED, urgency)
    }

    @Test
    fun `computeUrgency returns RED when progress exceeds interval`() {
        // Пробег больше интервала — типичная ситуация "просрочено"
        val item = Fixtures.item(
            intervalKm = 10_000,
            lastServiceKm = 0,
            currentKm = 15_000
        )

        val urgency = useCase.computeUrgency(item, nowMillis = Fixtures.NOW)

        assertEquals(ServiceItem.URGENCY_RED, urgency)
    }

    @Test
    fun `computeUrgency respects custom yellowThreshold`() {
        // 60% от интервала. По дефолту (0.8) было бы GREEN, но с порогом 0.5 — YELLOW
        val item = Fixtures.item(
            intervalKm = 10_000,
            lastServiceKm = 0,
            currentKm = 6_000
        )

        val urgency = useCase.computeUrgency(
            item = item,
            nowMillis = Fixtures.NOW,
            yellowThreshold = 0.5f
        )

        assertEquals(ServiceItem.URGENCY_YELLOW, urgency)
    }

    @Test
    fun `computeUrgency takes max of km and month progress`() {
        // По пробегу — 10% (1000 из 10000), зелёный.
        // По месяцам — 11 месяцев при интервале 12 = ~92%, жёлтый.
        // Итог должен быть жёлтый, потому что берётся max.
        val elevenMonthsAgo = Fixtures.NOW - 11L * 30 * Fixtures.DAY_MS
        val item = Fixtures.item(
            intervalKm = 10_000,
            intervalMonths = 12,
            lastServiceKm = 0,
            lastServiceDateMillis = elevenMonthsAgo,
            currentKm = 1_000
        )

        val urgency = useCase.computeUrgency(item, nowMillis = Fixtures.NOW)

        assertEquals(ServiceItem.URGENCY_YELLOW, urgency)
    }

    @Test
    fun `computeUrgency handles zero interval without crash`() {
        // intervalKm = 0 — защита от деления на ноль. Должно вернуть GREEN (kmProgress = 0).
        val item = Fixtures.item(
            intervalKm = 0,
            intervalMonths = 0,
            currentKm = 100_000
        )

        val urgency = useCase.computeUrgency(item, nowMillis = Fixtures.NOW)

        assertEquals(ServiceItem.URGENCY_GREEN, urgency)
    }

    // ========================================================================
    // markServiceDone
    // ========================================================================

    @Test
    fun `markServiceDone resets km, date and sets urgency to GREEN`() {
        val oneYearAgo = Fixtures.NOW - 365 * Fixtures.DAY_MS
        val old = Fixtures.item(
            intervalKm = 10_000,
            lastServiceKm = 0,
            lastServiceDateMillis = oneYearAgo,
            currentKm = 15_000,
            urgency = ServiceItem.URGENCY_RED
        )

        val updated = useCase.markServiceDone(
            item = old,
            currentKm = 15_000,
            nowMillis = Fixtures.NOW
        )

        assertEquals(15_000, updated.currentKm)
        assertEquals(15_000, updated.lastServiceKm)
        assertEquals(Fixtures.NOW, updated.lastServiceDateMillis)
        assertEquals(ServiceItem.URGENCY_GREEN, updated.urgency)
        // id и остальные поля не должны поменяться
        assertEquals(old.id, updated.id)
        assertEquals(old.name, updated.name)
        assertEquals(old.intervalKm, updated.intervalKm)
    }

    // ========================================================================
    // generateInitialServices
    // ========================================================================

    @Test
    fun `generateInitialServices finds regulation by car name`() {
        val regs = listOf(Fixtures.toyotaRegulation(), Fixtures.bmwRegulation())

        val items = useCase.generateInitialServices(
            regulations = regs,
            selectedCar = "Toyota",
            currentKm = 0,
            nowMillis = Fixtures.NOW
        )

        assertEquals(3, items.size)
        assertTrue(items.all { it.car == "Toyota" })
        assertTrue(items.any { it.name == "Замена масла" && it.intervalKm == 10_000 })
    }

    @Test
    fun `generateInitialServices is case-insensitive on car name`() {
        val regs = listOf(Fixtures.toyotaRegulation())

        val items = useCase.generateInitialServices(
            regulations = regs,
            selectedCar = "toyota", // lowercase
            currentKm = 0,
            nowMillis = Fixtures.NOW
        )

        assertEquals(3, items.size)
    }

    @Test
    fun `generateInitialServices returns empty list for unknown car`() {
        val regs = listOf(Fixtures.toyotaRegulation())

        val items = useCase.generateInitialServices(
            regulations = regs,
            selectedCar = "Lada",
            currentKm = 0,
            nowMillis = Fixtures.NOW
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `generateInitialServices sets initial urgency based on currentKm`() {
        val regs = listOf(Fixtures.toyotaRegulation())

        // Пробег 9000 из 10000 интервала для масла = 90%, должно быть YELLOW
        val items = useCase.generateInitialServices(
            regulations = regs,
            selectedCar = "Toyota",
            currentKm = 9_000,
            nowMillis = Fixtures.NOW
        )

        val oil = items.first { it.name == "Замена масла" }
        assertEquals(ServiceItem.URGENCY_YELLOW, oil.urgency)

        val filter = items.first { it.name == "Воздушный фильтр" } // интервал 30000, 9000 = 30% -> GREEN
        assertEquals(ServiceItem.URGENCY_GREEN, filter.urgency)
    }

    // ========================================================================
    // recalculateAllUrgency
    // ========================================================================

    @Test
    fun `recalculateAllUrgency updates currentKm and urgency for all items`() {
        val items = listOf(
            Fixtures.item(id = 1, intervalKm = 10_000, currentKm = 0, urgency = ServiceItem.URGENCY_GREEN),
            Fixtures.item(id = 2, intervalKm = 10_000, currentKm = 0, urgency = ServiceItem.URGENCY_GREEN)
        )

        val updated = useCase.recalculateAllUrgency(
            items = items,
            newCurrentKm = 9_000,
            nowMillis = Fixtures.NOW
        )

        assertEquals(2, updated.size)
        assertTrue(updated.all { it.currentKm == 9_000 })
        // 9000 из 10000 = 90% -> YELLOW для обоих
        assertTrue(updated.all { it.urgency == ServiceItem.URGENCY_YELLOW })
    }

    @Test
    fun `recalculateAllUrgency does not mutate original list`() {
        val original = Fixtures.item(id = 1, intervalKm = 10_000, currentKm = 0)
        val list = listOf(original)

        useCase.recalculateAllUrgency(list, newCurrentKm = 9_000, nowMillis = Fixtures.NOW)

        // Оригинал не должен измениться — data class иммутабельный
        assertEquals(0, original.currentKm)
        assertEquals(ServiceItem.URGENCY_GREEN, original.urgency)
    }

    // ========================================================================
    // createRecord
    // ========================================================================

    @Test
    fun `createRecord fills all fields`() {
        val record = useCase.createRecord(
            itemId = 42L,
            km = 12_345,
            nowMillis = Fixtures.NOW,
            note = "test note"
        )

        assertEquals(42L, record.serviceItemId)
        assertEquals(12_345, record.km)
        assertEquals(Fixtures.NOW, record.dateMillis)
        assertEquals("test note", record.note)
        // id autoGenerate — в новой записи должен быть 0
        assertEquals(0L, record.id)
    }

    @Test
    fun `createRecord uses empty note by default`() {
        val record = useCase.createRecord(itemId = 1L, km = 100, nowMillis = Fixtures.NOW)
        assertNotNull(record.note)
        assertEquals("", record.note)
    }
}