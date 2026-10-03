package com.mamblxon.autosense.domain.usecase

class ValidationTest {

    private lateinit var useCase: ServiceUseCase

    @Before
    fun setUp() {
        useCase = ServiceUseCase()
    }

    // ---------- validateKm ----------

    @Test
    fun `validateKm accepts positive number`() {
        assertEquals(ValidationResult.Valid, useCase.validateKm("50000"))
    }

    @Test
    fun `validateKm accepts zero`() {
        assertEquals(ValidationResult.Valid, useCase.validateKm("0"))
    }

    @Test
    fun `validateKm rejects empty`() {
        val result = useCase.validateKm("")
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateKm rejects non-numeric`() {
        assertTrue(useCase.validateKm("abc") is ValidationResult.Invalid)
    }

    @Test
    fun `validateKm rejects negative`() {
        assertTrue(useCase.validateKm("-100") is ValidationResult.Invalid)
    }

    @Test
    fun `validateKm rejects above max`() {
        assertTrue(useCase.validateKm("99999999") is ValidationResult.Invalid)
    }

    @Test
    fun `validateKm rejects below lastService`() {
        val result = useCase.validateKm("40000", lastServiceKm = 50000)
        assertTrue(result is ValidationResult.Invalid)
        val msg = (result as ValidationResult.Invalid).error
        assertTrue(msg.contains("50000"))
    }

    @Test
    fun `validateKm accepts equal to lastService`() {
        assertEquals(
            ValidationResult.Valid,
            useCase.validateKm("50000", lastServiceKm = 50000)
        )
    }

    @Test
    fun `validateKm trims whitespace`() {
        assertEquals(ValidationResult.Valid, useCase.validateKm("  5000  "))
    }

    @Test
    fun `validateKm with allowZero=false rejects zero`() {
        assertTrue(
            useCase.validateKm("0", allowZero = false) is ValidationResult.Invalid
        )
    }

    // ---------- validateIntervalKm ----------

    @Test
    fun `validateIntervalKm accepts zero`() {
        assertEquals(ValidationResult.Valid, useCase.validateIntervalKm("0"))
    }

    @Test
    fun `validateIntervalKm rejects negative`() {
        assertTrue(useCase.validateIntervalKm("-1") is ValidationResult.Invalid)
    }

    // ---------- validateIntervalMonths ----------

    @Test
    fun `validateIntervalMonths rejects above max`() {
        assertTrue(useCase.validateIntervalMonths("200") is ValidationResult.Invalid)
    }

    @Test
    fun `validateIntervalMonths accepts 12`() {
        assertEquals(ValidationResult.Valid, useCase.validateIntervalMonths("12"))
    }

    // ---------- validateIntervalsPair ----------

    @Test
    fun `validateIntervalsPair rejects both zero`() {
        val result = useCase.validateIntervalsPair("0", "0")
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `validateIntervalsPair accepts km only`() {
        assertEquals(ValidationResult.Valid, useCase.validateIntervalsPair("10000", "0"))
    }

    @Test
    fun `validateIntervalsPair accepts months only`() {
        assertEquals(ValidationResult.Valid, useCase.validateIntervalsPair("0", "12"))
    }

    @Test
    fun `validateIntervalsPair accepts both non-zero`() {
        assertEquals(ValidationResult.Valid, useCase.validateIntervalsPair("10000", "12"))
    }

    @Test
    fun `validateIntervalsPair rejects invalid km first`() {
        assertTrue(useCase.validateIntervalsPair("abc", "12") is ValidationResult.Invalid)
    }

    // ---------- validateName ----------

    @Test
    fun `validateName rejects empty`() {
        assertTrue(useCase.validateName("") is ValidationResult.Invalid)
    }

    @Test
    fun `validateName rejects whitespace only`() {
        assertTrue(useCase.validateName("   ") is ValidationResult.Invalid)
    }

    @Test
    fun `validateName rejects too short`() {
        assertTrue(useCase.validateName("A") is ValidationResult.Invalid)
    }

    @Test
    fun `validateName accepts normal name`() {
        assertEquals(ValidationResult.Valid, useCase.validateName("Замена масла"))
    }

    @Test
    fun `validateName rejects too long`() {
        val longName = "A".repeat(200)
        assertTrue(useCase.validateName(longName) is ValidationResult.Invalid)
    }

    // ---------- parseIntOrNull ----------

    @Test
    fun `parseIntOrNull returns null for invalid`() {
        assertEquals(null, useCase.parseIntOrNull("abc"))
    }

    // ---------- parseDate: високосные годы (пункт 12) ----------

    @Test
    fun `parseDate accepts leap day of leap year`() {
        // 2024 — високосный: 29.02 существует
        val date = useCase.parseDate("29.02.2024")
        assertNotNull(date)
    }

    @Test
    fun `parseDate rejects feb 29 in non-leap year`() {
        // 2023 — не високосный: 29.02 быть не может (isLenient=false должен отклонить)
        assertEquals(null, useCase.parseDate("29.02.2023"))
    }

    @Test
    fun `parseDate rejects impossible dates`() {
        assertEquals(null, useCase.parseDate("31.02.2026"))
        assertEquals(null, useCase.parseDate("00.01.2026"))
        assertEquals(null, useCase.parseDate("01.13.2026"))
    }

    @Test
    fun `validateDateNotFuture rejects feb 29 in non-leap year`() {
        val now = System.currentTimeMillis()
        assertTrue(
            useCase.validateDateNotFuture("29.02.2023", nowMillis = now)
                is ValidationResult.Invalid
        )
    }

    @Test
    fun `parseIntOrNull parses valid with trim`() {
        assertEquals(42, useCase.parseIntOrNull("  42  "))
    }
}