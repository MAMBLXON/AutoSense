package com.mamblxon.autosense.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.prefs.AppPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ServiceUiState(
    val listItems: List<ServiceListItem> = emptyList(),
    val isEmpty: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentCar: String = "",
    val filter: ServiceFilter = ServiceFilter.ALL,
    val totals: Totals = Totals(),
    val searchQuery: String = ""
)

data class Totals(
    val all: Int = 0,
    val green: Int = 0,
    val yellow: Int = 0,
    val red: Int = 0
)

/** Промежуточный снимок настроек — чтобы combine был из 3 источников, а не 6. */
private data class SettingsSnapshot(
    val filter: ServiceFilter,
    val query: String,
    val error: String?,
    val loading: Boolean
)

sealed interface UiEvent {
    data class MarkDone(
        val item: ServiceItem,
        val currentKm: Int,
        val cost: Int = 0,
        val note: String = ""
    ) : UiEvent

    data class UpdateKm(val newKm: Int) : UiEvent
    data class DeleteItem(val item: ServiceItem) : UiEvent
    data class SetFilter(val filter: ServiceFilter) : UiEvent
    data class SetSearchQuery(val query: String) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val useCase = ServiceUseCase()
    private val prefs = AppPrefs(app)

    private val currentProfileFlow = MutableStateFlow(prefs.currentProfileId)
    private val filterFlow = MutableStateFlow(ServiceFilter.ALL)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val loadingFlow = MutableStateFlow(true)
    private val searchQueryFlow = MutableStateFlow("")

    private val seedMutex = Mutex()

    @OptIn(FlowPreview::class)
    private val debouncedQuery = searchQueryFlow
        .debounce(300L)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ServiceUiState> = currentProfileFlow
        .flatMapLatest { profileId ->
            if (profileId <= 0L) {
                return@flatMapLatest flowOf(
                    ServiceUiState(isLoading = false, isEmpty = true, currentCar = "")
                )
            }

            val carNameFlow = repo.observeProfiles().map { profiles ->
                profiles.firstOrNull { it.id == profileId }?.catalogId ?: ""
            }

            // 1. Сжимаем 4 настройки в один snapshot — это отдельный combine из 4 flow.
            val settingsFlow = combine(
                filterFlow,
                debouncedQuery,
                errorFlow,
                loadingFlow
            ) { filter, query, error, loading ->
                SettingsSnapshot(filter, query, error, loading)
            }

            // 2. Финальный combine из 3 источников — гарантированно рабочий overload.
            combine(
                repo.observeItems(profileId),
                carNameFlow,
                settingsFlow
            ) { items, catalogId, settings ->
                buildState(
                    items = items,
                    catalogId = catalogId,
                    filter = settings.filter,
                    query = settings.query,
                    error = settings.error,
                    loading = settings.loading
                )
            }.onStart {
                emit(ServiceUiState(isLoading = true, currentCar = ""))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ServiceUiState()
        )

    private fun buildState(
        items: List<ServiceItem>,
        catalogId: String,
        filter: ServiceFilter,
        query: String,
        error: String?,
        loading: Boolean
    ): ServiceUiState {
        val totals = Totals(
            all = items.size,
            green = items.count { it.urgency == ServiceItem.URGENCY_GREEN },
            yellow = items.count { it.urgency == ServiceItem.URGENCY_YELLOW },
            red = items.count { it.urgency == ServiceItem.URGENCY_RED }
        )

        val byFilter = items.filter { filter.matches(it) }
        val bySearch = if (query.isBlank()) byFilter
        else byFilter.filter { it.name.contains(query, ignoreCase = true) }

        val order = listOf(
            "Двигатель", "Топливная система", "Тормоза",
            "Трансмиссия", "Ходовая", "Электрика", "Прочее"
        )
        val grouped = bySearch
            .groupBy { it.category.ifBlank { "Прочее" } }
            .toSortedMap(compareBy { c ->
                order.indexOf(c).let { if (it == -1) order.size else it }
            })

        val listItems = buildList {
            grouped.forEach { (cat, groupItems) ->
                if (groupItems.isNotEmpty()) {
                    val urgent = groupItems.any {
                        it.urgency == ServiceItem.URGENCY_RED ||
                                it.urgency == ServiceItem.URGENCY_YELLOW
                    }
                    add(ServiceListItem.Header(cat, groupItems.size, urgent))
                    groupItems.forEach { add(ServiceListItem.Row(it)) }
                }
            }
        }

        return ServiceUiState(
            listItems = listItems,
            isEmpty = listItems.isEmpty(),
            isLoading = loading,
            error = error,
            currentCar = catalogId,
            filter = filter,
            totals = totals,
            searchQuery = query
        )
    }

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            try {
                val id = prefs.currentProfileId
                if (id > 0) seedIfEmpty(id)
                loadingFlow.value = false
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Unknown error"
                loadingFlow.value = false
            }
        }
    }

    private suspend fun seedIfEmpty(profileId: Long) {
        seedMutex.withLock {
            if (repo.countItems(profileId) != 0) return
            val profile = repo.getProfileById(profileId) ?: return
            val catalog = repo.loadCatalog()
            val carModel = catalog.cars.firstOrNull { it.id == profile.catalogId } ?: return
            val generated = useCase.generateFromCatalog(
                car = carModel,
                profileId = profileId,
                currentKm = profile.currentKm
            )
            repo.insertItems(generated)
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            val profileId = currentProfileFlow.value
            when (event) {
                is UiEvent.MarkDone -> {
                    val updated = useCase.markServiceDone(event.item, event.currentKm)
                    repo.updateItem(updated)
                    repo.insertRecord(
                        useCase.createRecord(
                            itemId = updated.id,
                            km = event.currentKm,
                            note = event.note.ifBlank { "Пробег: ${event.currentKm} км" },
                            cost = event.cost
                        )
                    )
                    prefs.setLastKm(profileId, event.currentKm)
                    prefs.setLastKmUpdate(profileId, System.currentTimeMillis())
                }

                is UiEvent.UpdateKm -> {
                    val items = repo.getItemsOnce(profileId)
                    if (items.isNotEmpty()) {
                        val threshold = prefs.yellowThreshold
                        val now = System.currentTimeMillis()
                        val recalculated = items.map { old ->
                            val withKm = old.copy(currentKm = event.newKm)
                            withKm.copy(
                                urgency = useCase.computeUrgency(withKm, now, threshold)
                            )
                        }
                        repo.updateItems(recalculated)
                    }
                    prefs.setLastKm(profileId, event.newKm)
                    prefs.setLastKmUpdate(profileId, System.currentTimeMillis())
                }

                is UiEvent.DeleteItem -> repo.deleteItem(event.item)
                is UiEvent.SetFilter -> filterFlow.value = event.filter
                is UiEvent.SetSearchQuery -> searchQueryFlow.value = event.query
            }
        }
    }

    fun reloadForCurrentProfile() {
        currentProfileFlow.value = prefs.currentProfileId
        loadingFlow.value = true
        viewModelScope.launch {
            try {
                seedIfEmpty(prefs.currentProfileId)
            } finally {
                loadingFlow.value = false
            }
        }
    }
}