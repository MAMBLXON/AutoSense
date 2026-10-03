package com.mamblxon.autosense.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.prefs.AppPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
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
    /** Список пуст из-за поиска — показывать «Ничего не найдено». */
    val isNoSearchResults: Boolean = false,
    /** Список пуст из-за фильтра (тип работ) при непустой базе. */
    val isFilteredEmpty: Boolean = false,
    /** В базе нет ни одной записи — показывать заглушку «Добавь первую запись». */
    val isDatabaseEmpty: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentCar: String = "",
    val filter: ServiceFilter = ServiceFilter.ALL,
    val totals: Totals = Totals(),
    val searchQuery: String = "",
    val typeFilter: Set<ServiceType> = emptySet(),
    val sortOrder: SortOrder = SortOrder.DATE_DESC
)

data class Totals(
    val all: Int = 0,
    val green: Int = 0,
    val yellow: Int = 0,
    val red: Int = 0
)

enum class SortOrder(val label: String) {
    DATE_DESC("По дате (новые сначала)"),
    DATE_ASC("По дате (старые сначала)"),
    KM_ASC("По пробегу (возрастание)"),
    KM_DESC("По пробегу (убывание)")
}

/** Промежуточный снимок настроек — чтобы combine был из 3 источников, а не 6. */
private data class SettingsSnapshot(
    val filter: ServiceFilter,
    val query: String,
    val error: String?,
    val loading: Boolean,
    val types: Set<ServiceType>,
    val sort: SortOrder
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
    /** Восстановление записи после «Отмены» в Snackbar (undo для свайп-удаления). */
    data class RestoreItem(val item: ServiceItem) : UiEvent
    /** Обновление полей работы (долгое нажатие / свайп вправо → диалог редактирования). */
    data class EditItem(
        val item: ServiceItem,
        val name: String,
        val intervalKm: Int,
        val intervalMonths: Int,
        /** Миллисекунды последней даты ТО; null — оставить прежнюю дату. */
        val lastServiceDateMillis: Long? = null,
        /** Новая строка стоимости; null — не менять заметку, "" — очистить стоимость. */
        val costLine: String? = null
    ) : UiEvent
    data class SetFilter(val filter: ServiceFilter) : UiEvent
    data class SetSearchQuery(val query: String) : UiEvent
    /** Фильтр по типам работ (диалог с чекбоксами). Пустой набор = без фильтра. */
    data class SetTypeFilter(val types: Set<ServiceType>) : UiEvent
    data class SetSortOrder(val order: SortOrder) : UiEvent
}

/** Одноразовые сообщения для UI (Snackbar). */
sealed interface UiMessage {
    val text: String

    data class Error(override val text: String) : UiMessage
    data class Info(override val text: String) : UiMessage
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
    /** Фильтр по типам работ (чекбоксы диалога «Фильтр»). Пустой набор = без фильтра. */
    private val typeFilterFlow = MutableStateFlow<Set<ServiceType>>(emptySet())
    /** Сортировка; выбранная сортировка сохраняется в SharedPreferences. */
    private val sortFlow = MutableStateFlow(prefs.sortOrder)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

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
                    ServiceUiState(
                        isLoading = false,
                        isEmpty = true,
                        isDatabaseEmpty = true,
                        currentCar = ""
                    )
                )
            }

            // carNameFlow отдаёт Flow<String>, поэтому catch здесь — свой, с типом String.
            val carNameFlow: Flow<String> = repo.observeProfiles().map { profiles ->
                profiles.firstOrNull { it.id == profileId }?.catalogId ?: ""
            }.catch { t ->
                Log.e("AutoSense", "DB observe profiles failed for profile $profileId", t)
                emit("")
            }

            // Поток записей: при сбое БД не обрывается, а отдаёт пустой список
            // и публикует понятное сообщение (см. catchItemsFlow).
            val itemsFlow: Flow<List<ServiceItem>> =
                repo.observeItems(profileId).catchItemsFlow(profileId)

            // Сжимаем настройки в один snapshot: combine из явных типов-лямбда,
            // чтобы вывод типов не зависел от количества источников.
            val filtersFlow: Flow<Triple<ServiceFilter, String, Set<ServiceType>>> =
                combine(filterFlow, debouncedQuery, typeFilterFlow) { f, q, t ->
                    Triple(f, q, t)
                }
            val errorLoadingFlow: Flow<Pair<String?, Boolean>> =
                combine(errorFlow, loadingFlow) { e, l -> e to l }
            val settingsFlow: Flow<SettingsSnapshot> =
                combine(filtersFlow, errorLoadingFlow, sortFlow) { triple, pair, sort ->
                    SettingsSnapshot(
                        filter = triple.first,
                        query = triple.second,
                        error = pair.first,
                        loading = pair.second,
                        types = triple.third,
                        sort = sort
                    )
                }

            // Финальный combine из 3 источников.
            combine(itemsFlow, carNameFlow, settingsFlow) { items, catalogId, settings ->
                buildState(
                    items = items,
                    catalogId = catalogId,
                    filter = settings.filter,
                    query = settings.query,
                    error = settings.error,
                    loading = settings.loading,
                    types = settings.types,
                    sort = settings.sort
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

    /**
     * Защита потока наблюдения от сбоев БД: при исключении поток не обрывается
     * (иначе был бы белый экран без какой-либо реакции), а отдаёт пустой список
     * и публикует понятное сообщение об ошибке.
     */
    private fun <T> Flow<List<T>>.catchItemsFlow(profileId: Long): Flow<List<T>> =
        this.catch { t ->
            Log.e("AutoSense", "DB observe failed for profile $profileId", t)
            errorFlow.value = "Ошибка базы данных"
            _messages.tryEmit(UiMessage.Error("Не удалось загрузить данные из базы"))
            emit(emptyList())
        }

    private fun buildState(
        items: List<ServiceItem>,
        catalogId: String,
        filter: ServiceFilter,
        query: String,
        error: String?,
        loading: Boolean,
        types: Set<ServiceType>,
        sort: SortOrder
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
        // Фильтр по типу работ (несколько типов = «или»; OTHER = всё, что не попало под другие типы).
        val byType = if (types.isEmpty()) bySearch
        else bySearch.filter { item -> types.any { it.matches(item.name, item.category) } }

        // Сортировка внутри группы (между собой записи сравниваются по выбранному порядку;
        // срочность остаётся первичным критерием группировки по категориям).
        val sorted = when (sort) {
            SortOrder.DATE_DESC -> byType.sortedByDescending { it.lastServiceDateMillis }
            SortOrder.DATE_ASC -> byType.sortedBy { it.lastServiceDateMillis }
            SortOrder.KM_ASC -> byType.sortedBy { it.currentKm }
            SortOrder.KM_DESC -> byType.sortedByDescending { it.currentKm }
        }

        val order = listOf(
            "Двигатель", "Топливная система", "Тормоза",
            "Трансмиссия", "Ходовая", "Электрика", "Прочее"
        )
        val grouped = sorted
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
            isNoSearchResults = listItems.isEmpty() && !loading &&
                    items.isNotEmpty() && query.isNotBlank(),
            isFilteredEmpty = listItems.isEmpty() && !loading &&
                    items.isNotEmpty() && query.isBlank(),
            isDatabaseEmpty = items.isEmpty(),
            isLoading = loading,
            error = error,
            currentCar = catalogId,
            filter = filter,
            totals = totals,
            searchQuery = query,
            typeFilter = types,
            sortOrder = sort
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
                errorFlow.value = null
                loadingFlow.value = false
            } catch (t: Throwable) {
                Log.e("AutoSense", "Bootstrap failed", t)
                errorFlow.value = "Не удалось загрузить данные"
                _messages.tryEmit(UiMessage.Error("Ошибка базы данных: ${t.message ?: "неизвестная ошибка"}"))
                loadingFlow.value = false
            }
        }
    }

    private suspend fun seedIfEmpty(profileId: Long) {
        if (profileId <= 0L) return
        seedMutex.withLock {
            try {
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
            } catch (t: Throwable) {
                Log.e("AutoSense", "Failed to seed services for profile $profileId", t)
                _messages.tryEmit(UiMessage.Error("Не удалось подготовить список ТО"))
            }
        }
    }

    /**
     * Единая точка обработки ошибок БД для write-операций:
     * логируем в консоль и показываем понятное сообщение (Snackbar), а не системный краш.
     */
    private suspend fun dbOp(fallbackMessage: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e("AutoSense", "$fallbackMessage (DB operation failed)", t)
            errorFlow.value = "Ошибка базы данных"
            _messages.tryEmit(UiMessage.Error(fallbackMessage))
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            val profileId = currentProfileFlow.value
            when (event) {
                is UiEvent.MarkDone -> dbOp("Не удалось сохранить запись") {
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
                    _messages.tryEmit(UiMessage.Info("ТО отмечено: ${event.item.name}"))
                }

                is UiEvent.UpdateKm -> dbOp("Не удалось обновить пробег") {
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

                is UiEvent.DeleteItem -> deleteItem(event.item)

                is UiEvent.RestoreItem -> dbOp("Не удалось восстановить запись") {
                    repo.insertItem(event.item)
                    _messages.tryEmit(UiMessage.Info("Запись восстановлена"))
                }

                is UiEvent.EditItem -> updateItem(event)

                is UiEvent.SetFilter -> filterFlow.value = event.filter
                is UiEvent.SetSearchQuery -> searchQueryFlow.value = event.query

                is UiEvent.SetTypeFilter -> typeFilterFlow.value = event.types

                is UiEvent.SetSortOrder -> {
                    sortFlow.value = event.order
                    // Выбранная сортировка сохраняется между запусками приложения.
                    prefs.setSortOrder(event.order)
                }
            }
        }
    }

    /**
     * Подсчёт записей, подходящих под выбранные типы работ — для диалога фильтра.
     * Ошибки БД не допускаем до UI: при сбое возвращаем пустой профиль-набор и логируем.
     */
    suspend fun itemTypeCounts(): Map<ServiceType, Int> {
        val profileId = currentProfileFlow.value
        if (profileId <= 0L) return emptyMap()
        return try {
            val items = repo.getItemsOnce(profileId)
            ServiceType.entries.associateWith { type ->
                items.count { type.matches(it.name, it.category) }
            }
        } catch (t: Throwable) {
            Log.e("AutoSense", "Failed to load items for type counts", t)
            emptyMap()
        }
    }

    /** Удаление записи (свайп влево). Ошибку БД гасим Snackbar-ом, не крашем. */
    fun deleteItem(item: ServiceItem) {
        viewModelScope.launch {
            dbOp("Не удалось удалить запись") {
                repo.deleteItem(item)
            }
        }
    }

    /**
     * Обновление работы (долгое нажатие / свайп вправо → диалог редактирования).
     * После смены полей пересчитываем срочность, чтобы бейдж сразу отражал новые правила.
     * Список обновляется автоматически через Room-Flow — без перезагрузки активности.
     */
    fun updateItem(event: UiEvent.EditItem) {
        viewModelScope.launch {
            dbOp("Не удалось сохранить запись") {
                val updated = event.item.copy(
                    name = event.name.trim(),
                    intervalKm = event.intervalKm,
                    intervalMonths = event.intervalMonths,
                    lastServiceDateMillis = event.lastServiceDateMillis
                        ?: event.item.lastServiceDateMillis,
                    notes = when {
                        event.costLine == null -> event.item.notes
                        event.costLine.isBlank() -> ""
                        else -> "Стоимость последнего ТО: ${event.costLine.trim()} ₽"
                    }
                ).let {
                    it.copy(urgency = useCase.computeUrgency(it, System.currentTimeMillis(), prefs.yellowThreshold))
                }
                repo.updateItem(updated)
                _messages.tryEmit(UiMessage.Info("Запись обновлена: ${updated.name}"))
            }
        }
    }

    fun reloadForCurrentProfile() {
        currentProfileFlow.value = prefs.currentProfileId
        loadingFlow.value = true
        viewModelScope.launch {
            try {
                seedIfEmpty(prefs.currentProfileId)
                errorFlow.value = null
            } catch (t: Throwable) {
                Log.e("AutoSense", "Reload failed", t)
                errorFlow.value = "Не удалось загрузить данные"
                _messages.tryEmit(UiMessage.Error("Ошибка базы данных: ${t.message ?: "неизвестная ошибка"}"))
            } finally {
                loadingFlow.value = false
            }
        }
    }
}