package com.mamblxon.autosense.ui.main.add

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.domain.usecase.ValidationResult
import com.mamblxon.autosense.prefs.AppPrefs
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Поля формы «Добавить ТО». Значения — сырые строки из TextInputEditText,
 * чтобы валидация и подсчёт canSave работали от одного источника.
 */
data class AddForm(
    val name: String = "",
    val intervalKm: String = "",
    val intervalMonths: String = "",
    val lastDate: String = "",   // ДД.ММ.ГГГГ, необязательное
    val cost: String = ""        // ₽, необязательное
)

sealed interface SaveOutcome {
    data object Success : SaveOutcome
    data class Failure(val message: String) : SaveOutcome
}

data class AddUiState(
    val form: AddForm = AddForm(),
    val carName: String = "",
    val currentKm: Int = 0,
    /** Кнопка «Сохранить» неактивна, пока все обязательные поля не валидны. */
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    /** Одноразовое сообщение для Snackbar (ошибки БД и т.п.). */
    val message: String? = null
)

/**
 * ViewModel экрана добавления новой работы (ТО).
 * Валидирует ввод ДО вызова репозитория и оборачивает операции с БД
 * в try-catch, возвращая понятный результат вместо краша.
 */
class AddViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val useCase = ServiceUseCase()
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    /**
     * Атомарный guard от race condition двойного нажатия «Сохранить»:
     * compareAndSet гарантирует, что второй клик за 100 мс не запустит
     * вторую корутину и не создаст дубликат записи.
     */
    private val savingGuard = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            try {
                val profileId = prefs.currentProfileId
                if (profileId > 0L) {
                    val profile = repo.getProfileById(profileId)
                    val catalogId = profile?.catalogId.orEmpty()
                    val fallbackKm = profile?.currentKm ?: 0
                    val knownKm = prefs.resolveKnownKm(profileId, fallbackKm)
                    _state.value = _state.value.copy(carName = catalogId, currentKm = knownKm)
                }
            } catch (t: Throwable) {
                Log.e("AutoSense", "AddViewModel: failed to load profile", t)
            }
        }
    }

    fun onFormChanged(form: AddForm) {
        _state.value = _state.value.copy(form = form, canSave = validate(form).isValid)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Полная проверка формы. Первый Invalid — причина неактивной кнопки. */
    fun validate(form: AddForm): ValidationResult {
        useCase.validateName(form.name).let { if (it is ValidationResult.Invalid) return it }
        useCase.validateIntervalKm(form.intervalKm).let { if (it is ValidationResult.Invalid) return it }
        useCase.validateIntervalMonths(form.intervalMonths).let { if (it is ValidationResult.Invalid) return it }
        if (!useCase.hasPositiveInterval(form.intervalKm, form.intervalMonths)) {
            return ValidationResult.Invalid("Хотя бы один интервал должен быть больше нуля")
        }
        if (form.lastDate.isNotBlank()) {
            useCase.validateDateNotFuture(form.lastDate)
                .let { if (it is ValidationResult.Invalid) return it }
        }
        if (form.cost.isNotBlank()) {
            useCase.validateCost(form.cost).let { if (it is ValidationResult.Invalid) return it }
        }
        return ValidationResult.Valid
    }

    /** Сохранение только после успешной валидации; ошибки БД → SaveOutcome.Failure. */
    fun save(onResult: (SaveOutcome) -> Unit) {
        val st = _state.value
        val form = st.form

        // Атомарная проверка-захват: второй клик за 100 мс не пройдёт,
        // даже если первый ещё не успел выставить isSaving в состоянии.
        if (!savingGuard.compareAndSet(false, true)) return

        try {
            when (val v = validate(form)) {
                is ValidationResult.Invalid -> {
                    savingGuard.set(false)
                    onResult(SaveOutcome.Failure(v.error))
                    return
                }
                ValidationResult.Valid -> Unit
            }

            val profileId = prefs.currentProfileId
            if (profileId <= 0L) {
                savingGuard.set(false)
                onResult(SaveOutcome.Failure("Сначала создай профиль автомобиля"))
                return
            }

            _state.value = st.copy(isSaving = true)
            viewModelScope.launch {
                try {
                    val km = form.intervalKm.trim().toIntOrNull() ?: 0
                    val months = form.intervalMonths.trim().toIntOrNull() ?: 0
                    val lastServiceKm = if (km > 0) (st.currentKm - km).coerceAtLeast(0) else 0
                    val lastDateMillis: Long = if (form.lastDate.isBlank()) {
                        System.currentTimeMillis()
                    } else {
                        useCase.parseDate(form.lastDate)?.time ?: System.currentTimeMillis()
                    }

                    val base = ServiceItem(
                        profileId = profileId,
                        car = st.carName,
                        name = form.name.trim(),
                        category = "Прочее",
                        intervalKm = km,
                        intervalMonths = months,
                        lastServiceKm = lastServiceKm,
                        lastServiceDateMillis = lastDateMillis,
                        currentKm = st.currentKm,
                        notes = if (form.cost.isNotBlank()) {
                            "Стоимость последнего ТО: ${form.cost.trim()} ₽"
                        } else ""
                    )
                    val item = base.copy(urgency = useCase.computeUrgency(base))
                    repo.insertItems(listOf(item))
                    _state.value = _state.value.copy(isSaving = false, message = "Запись добавлена")
                    onResult(SaveOutcome.Success)
                } catch (t: Throwable) {
                    Log.e("AutoSense", "Failed to save service item", t)
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = "Не удалось сохранить запись"
                    )
                    onResult(SaveOutcome.Failure("Не удалось сохранить запись"))
                } finally {
                    // Сбрасываем guard в finally — иначе исключение между
                    // try/catch оставило бы кнопку «Сохранить» заблокированной навсегда.
                    withContext(NonCancellable) { savingGuard.set(false) }
                }
            }
        } catch (t: Throwable) {
            // Синхронный сбой до запуска корутины — освобождаем guard здесь.
            savingGuard.set(false)
            Log.e("AutoSense", "Failed to start save", t)
            _state.value = _state.value.copy(isSaving = false)
            throw t
        }
    }
}
