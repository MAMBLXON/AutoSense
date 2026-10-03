package com.mamblxon.autosense.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.data.remote.CarCatalog
import com.mamblxon.autosense.data.remote.CarModel
import com.mamblxon.autosense.data.remote.EngineSpec
import com.mamblxon.autosense.domain.model.CarProfile
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.prefs.AppPrefs
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServiceEntryState(
    val index: Int,
    val changed: Boolean = false,
    val lastKm: Int = 0
)

data class OnboardingSelection(
    val model: CarModel? = null,
    val year: Int = 2018,
    val engine: EngineSpec? = null,
    val transmission: String? = null,
    val km: Int = 0,
    val serviceEntries: List<ServiceEntryState> = emptyList()
) {
    val isStep2Complete: Boolean
        get() = model != null && engine != null &&
                transmission != null && km >= 0

    val changedCount: Int
        get() = serviceEntries.count { it.changed }
}

data class OnboardingUiState(
    val catalog: CarCatalog? = null,
    val selection: OnboardingSelection = OnboardingSelection(),
    val isSaving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val useCase = ServiceUseCase()
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Атомарный guard от race condition двойного нажатия «Создать профиль». */
    private val savingGuard = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            try {
                val catalog = repo.loadCatalog()
                _state.value = _state.value.copy(catalog = catalog)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "Ошибка загрузки каталога")
            }
        }
    }

    fun selectModel(model: CarModel) {
        val entries = model.serviceItems.indices.map { ServiceEntryState(index = it) }
        _state.value = _state.value.copy(
            selection = OnboardingSelection(
                model = model,
                year = 2018,
                engine = model.engines.firstOrNull(),
                transmission = model.transmissions.firstOrNull(),
                km = 0,
                serviceEntries = entries
            )
        )
    }

    fun setYear(year: Int) {
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(year = year)
        )
    }

    fun setEngine(engine: EngineSpec) {
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(engine = engine)
        )
    }

    fun setTransmission(tr: String) {
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(transmission = tr)
        )
    }

    fun setKm(km: Int) {
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(km = km)
        )
    }

    // ---------- Работа с отметками по работам ----------

    fun setServiceChanged(index: Int, changed: Boolean) {
        val list = _state.value.selection.serviceEntries.toMutableList()
        if (index !in list.indices) return
        val entry = list[index]
        val defaultKm = _state.value.selection.km
        list[index] = entry.copy(
            changed = changed,
            lastKm = if (changed && entry.lastKm <= 0) defaultKm else entry.lastKm
        )
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(serviceEntries = list)
        )
    }

    fun setServiceKm(index: Int, km: Int) {
        val list = _state.value.selection.serviceEntries.toMutableList()
        if (index !in list.indices) return
        list[index] = list[index].copy(lastKm = km.coerceAtLeast(0))
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(serviceEntries = list)
        )
    }

    fun markAllChanged(changed: Boolean) {
        val defaultKm = _state.value.selection.km
        val updated = _state.value.selection.serviceEntries.map { entry ->
            entry.copy(
                changed = changed,
                lastKm = if (changed) {
                    if (entry.lastKm > 0) entry.lastKm else defaultKm
                } else 0
            )
        }
        _state.value = _state.value.copy(
            selection = _state.value.selection.copy(serviceEntries = updated)
        )
    }

    // ---------- Сохранение ----------

    fun confirm() {
        val sel = _state.value.selection
        val model = sel.model ?: return
        val engine = sel.engine ?: return
        val tr = sel.transmission ?: return

        // Атомарный guard от двойного нажатия (compareAndSet вместо проверки флага в состоянии)
        if (!savingGuard.compareAndSet(false, true)) return
        _state.value = _state.value.copy(isSaving = true, error = null)

        viewModelScope.launch {
            try {
                val profile = CarProfile(
                    catalogId = model.id,
                    year = sel.year,
                    engineCode = engine.code,
                    transmission = tr,
                    currentKm = sel.km
                )
                val newProfileId = repo.saveProfile(profile)

                if (repo.countItems(newProfileId) == 0) {
                    val generated = useCase.generateFromCatalog(
                        car = model,
                        profileId = newProfileId,
                        currentKm = sel.km
                    )
                    val now = System.currentTimeMillis()
                    val finalItems = generated.mapIndexed { index, item ->
                        val entry = sel.serviceEntries.getOrNull(index)
                        if (entry?.changed == true) {
                            item.copy(lastServiceKm = entry.lastKm, lastServiceDateMillis = now)
                        } else item
                    }
                    repo.insertItems(finalItems)
                }

                // ВСЕГДА делаем созданный профиль активным.
                // Это единственный способ гарантировать, что MainActivity не вернёт в онбординг.
                prefs.currentProfileId = newProfileId
                prefs.setLastKm(newProfileId, sel.km)
                prefs.setLastKmUpdate(newProfileId, System.currentTimeMillis())

                _state.value = _state.value.copy(isSaving = false, done = true)
            } catch (t: Throwable) {
                android.util.Log.e("AutoSense", "Onboarding save failed", t)
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = t.message ?: "Не удалось сохранить профиль"
                )
            } finally {
                // Сброс guard'а в finally — при любом исходе кнопка разблокируется
                withContext(NonCancellable) { savingGuard.set(false) }
            }
        }
    }
}