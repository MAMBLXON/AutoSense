package com.mamblxon.autosense.domain.usecase

import com.mamblxon.autosense.data.remote.CarRegulation
import com.mamblxon.autosense.domain.model.ServiceItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ServiceUseCase {

    /**
     * Превращает "сырой" регламент из JSON в список конкретных задач ТО
     * для выбранной машины.
     */
    fun generateInitialServices(
        regulations: List<CarRegulation>,
        selectedCar: String,
        currentKm: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ServiceItem> {
        val regulation = regulations.firstOrNull {
            it.car.equals(selectedCar, ignoreCase = true)
        } ?: return emptyList()

        return regulation.services.map { spec ->
            val base = ServiceItem(
                car = regulation.car,
                name = spec.name,
                intervalKm = spec.intervalKm,
                intervalMonths = spec.intervalMonths,
                lastServiceKm = currentKm,
                lastServiceDateMillis = nowMillis,
                currentKm = currentKm
            )
            base.copy(urgency = computeUrgency(base, nowMillis))
        }
    }

    fun computeUrgency(
        item: ServiceItem,
        nowMillis: Long = System.currentTimeMillis(),
        yellowThreshold: Float = 0.8f
    ): Int {
        // Прогресс по км: если пробег вдруг меньше lastServiceKm
        // (пользователь «откатил» одометр), не уходим в минус.
        val kmProgress = if (item.intervalKm <= 0) 0.0
        else ((item.currentKm - item.lastServiceKm).toDouble() / item.intervalKm)
            .coerceAtLeast(0.0)

        val daysPassed = TimeUnit.MILLISECONDS.toDays(nowMillis - item.lastServiceDateMillis)
        val monthsPassed = daysPassed / 30.0
        val monthProgress = if (item.intervalMonths <= 0) 0.0
        else (monthsPassed / item.intervalMonths).coerceAtLeast(0.0)

        val progress = maxOf(kmProgress, monthProgress)

        return when {
            progress >= 1.0 -> ServiceItem.URGENCY_RED
            progress >= yellowThreshold -> ServiceItem.URGENCY_YELLOW
            else -> ServiceItem.URGENCY_GREEN
        }
    }
    fun markServiceDone(
        item: ServiceItem,
        currentKm: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): ServiceItem {
        return item.copy(
            lastServiceKm = currentKm,
            lastServiceDateMillis = nowMillis,
            currentKm = currentKm,
            urgency = ServiceItem.URGENCY_GREEN
        )
    }

    /**
     * Пересчитывает urgency для всех позиций после ввода нового пробега.
     */
    fun recalculateAllUrgency(
        items: List<ServiceItem>,
        newCurrentKm: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ServiceItem> {
        return items.map { old ->
            val withKm = old.copy(currentKm = newCurrentKm)
            withKm.copy(urgency = computeUrgency(withKm, nowMillis))
        }
    }
    fun createRecord(
        itemId: Long,
        km: Int,
        nowMillis: Long = System.currentTimeMillis(),
        note: String = "",
        cost: Int = 0
    ): com.mamblxon.autosense.domain.model.ServiceRecord {
        return com.mamblxon.autosense.domain.model.ServiceRecord(
            serviceItemId = itemId,
            km = km,
            dateMillis = nowMillis,
            note = note,
            cost = cost
        )
    }
    fun validateKm(
        input: String,
        lastServiceKm: Int? = null,
        allowZero: Boolean = true
    ): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Введи пробег")
        }
        val km = trimmed.toIntOrNull()
            ?: return ValidationResult.Invalid("Пробег должен быть целым числом")
        if (km < 0) {
            return ValidationResult.Invalid("Пробег не может быть отрицательным")
        }
        if (!allowZero && km == 0) {
            return ValidationResult.Invalid("Пробег должен быть больше нуля")
        }
        if (km > MAX_KM) {
            return ValidationResult.Invalid("Слишком большое значение (макс $MAX_KM)")
        }
        if (lastServiceKm != null && km < lastServiceKm) {
            return ValidationResult.Invalid(
                "Пробег не может быть меньше последнего ТО ($lastServiceKm км)"
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Валидирует интервал в километрах. 0 допустим, если интервал по месяцам > 0.
     */
    fun generateFromCatalog(
        car: com.mamblxon.autosense.data.remote.CarModel,
        profileId: Long,
        currentKm: Int,
        nowMillis: Long = System.currentTimeMillis(),
        yellowThreshold: Float = 0.8f
    ): List<ServiceItem> {
        return car.serviceItems.map { spec ->
            val base = ServiceItem(
                profileId = profileId,
                car = car.id,
                name = spec.name,
                category = spec.category,
                intervalKm = spec.intervalKm,
                intervalMonths = spec.intervalMonths,
                lastServiceKm = 0,
                lastServiceDateMillis = nowMillis,
                currentKm = currentKm,
                fluidSpec = spec.fluidSpec,
                volume = spec.volume,
                notes = spec.notes,
                urgency = ServiceItem.URGENCY_GREEN
            )
            base.copy(urgency = computeUrgency(base, nowMillis, yellowThreshold))
        }
    }
    fun validateIntervalKm(input: String, currentKm: Int = 0): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Введи интервал в км или 0")
        }
        val km = trimmed.toIntOrNull()
            ?: return ValidationResult.Invalid("Должно быть целое число")
        if (km < 0) {
            return ValidationResult.Invalid("Не может быть отрицательным")
        }
        if (km > MAX_KM) {
            return ValidationResult.Invalid("Слишком большое значение")
        }
        if (km > 0 && km > currentKm) {
            return ValidationResult.Invalid(
                "Интервал ($km км) не может быть больше текущего пробега ($currentKm км)"
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Валидирует интервал в месяцах.
     */
    fun validateIntervalMonths(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Введи интервал в месяцах или 0")
        }
        val months = trimmed.toIntOrNull()
            ?: return ValidationResult.Invalid("Должно быть целое число")
        if (months < 0) {
            return ValidationResult.Invalid("Не может быть отрицательным")
        }
        if (months > MAX_MONTHS) {
            return ValidationResult.Invalid("Не больше $MAX_MONTHS месяцев")
        }
        return ValidationResult.Valid
    }

    /**
     * Комбинированная проверка: хотя бы один интервал > 0.
     */
    fun validateIntervalsPair(
        kmInput: String,
        monthsInput: String
    ): ValidationResult {
        val kmResult = validateIntervalKm(kmInput)
        if (kmResult is ValidationResult.Invalid) return kmResult

        val monthsResult = validateIntervalMonths(monthsInput)
        if (monthsResult is ValidationResult.Invalid) return monthsResult

        val km = kmInput.trim().toIntOrNull() ?: 0
        val months = monthsInput.trim().toIntOrNull() ?: 0

        if (km == 0 && months == 0) {
            return ValidationResult.Invalid("Хотя бы один интервал должен быть больше нуля")
        }
        return ValidationResult.Valid
    }

    /**
     * Валидирует название работы.
     */
    fun validateName(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Название не может быть пустым")
        }
        if (trimmed.length < 2) {
            return ValidationResult.Invalid("Слишком короткое название")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return ValidationResult.Invalid("Не больше $MAX_NAME_LENGTH символов")
        }
        return ValidationResult.Valid
    }

    /**
     * Разбирает строку в Int. Возвращает null, если не получилось.
     * Утилита, чтобы не дублировать toIntOrNull() по всему коду.
     */
    fun parseIntOrNull(input: String): Int? = input.trim().toIntOrNull()

    /**
     * Валидирует дату в формате ДД.ММ.ГГГГ. Пустая строка допустима (поле необязательное).
     * Дата не может быть в будущем — «завтра сделать ТО» нельзя.
     */
    fun validateDateNotFuture(
        input: String,
        allowBlank: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return if (allowBlank) ValidationResult.Valid
            else ValidationResult.Invalid("Введи дату в формате ДД.ММ.ГГГГ")
        }
        val date = parseDate(trimmed)
            ?: return ValidationResult.Invalid("Формат даты: ДД.ММ.ГГГГ")
        if (date.time > nowMillis) {
            return ValidationResult.Invalid("Дата не может быть в будущем")
        }
        return ValidationResult.Valid
    }

    /**
     * Строго разбирает ДД.ММ.ГГГГ (несуществующие даты вроде 31.02.2026 отклоняются).
     * Возвращает null, если строка не является корректной датой.
     */
    fun parseDate(input: String): Date? {
        val format = SimpleDateFormat("dd.MM.yyyy", Locale.US).apply {
            isLenient = false
        }
        return runCatching { format.parse(input.trim()) }.getOrNull()
    }

    /** Валидирует стоимость: только число, больше нуля. Пустая строка допустима (необязательно). */
    fun validateCost(input: String, allowBlank: Boolean = true): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return if (allowBlank) ValidationResult.Valid
            else ValidationResult.Invalid("Введи стоимость")
        }
        val cost = trimmed.toIntOrNull()
            ?: return ValidationResult.Invalid("Стоимость должна быть числом")
        if (cost <= 0) {
            return ValidationResult.Invalid("Стоимость должна быть больше нуля")
        }
        if (cost > MAX_COST) {
            return ValidationResult.Invalid("Слишком большая сумма (макс $MAX_COST)")
        }
        return ValidationResult.Valid
    }

    /** True, если хотя бы один из двух интервалов задан числом больше нуля. */
    fun hasPositiveInterval(kmInput: String, monthsInput: String): Boolean {
        val km = kmInput.trim().toIntOrNull() ?: 0
        val months = monthsInput.trim().toIntOrNull() ?: 0
        return km > 0 || months > 0
    }

    companion object {
        const val MAX_KM = 9_999_999
        const val MAX_MONTHS = 120
        const val MAX_NAME_LENGTH = 100
        const val MAX_COST = 10_000_000
    }
}