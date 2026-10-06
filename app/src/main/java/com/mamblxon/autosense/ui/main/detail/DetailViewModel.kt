package com.mamblxon.autosense.ui.main.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.prefs.AppPrefs
import com.mamblxon.autosense.ui.main.UiMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: ServiceItem? = null,
    val history: List<ServiceRecord> = emptyList(),
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** Прогноз следующего ТО («~125 000 км или 15.11.2026») — считается в памяти. */
    val forecastText: String = "",
    /** Текст остатка для карточки деталей («Осталось: 5 000 км или 45 дней»). */
    val remainingText: String = ""
)

class DetailViewModel(
    app: Application,
    private val itemId: Long
) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val useCase = ServiceUseCase()
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Одноразовые UI-сообщения (тосты) об успехе/ошибке операций. */
    private val _messages = MutableSharedFlow<UiMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val item = repo.getItemById(itemId)
            if (item == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    notFound = true
                )
                return@launch
            }
            publishItem(item)

            repo.observeHistory(itemId).collect { list ->
                _state.value = _state.value.copy(history = list)
            }
        }
    }

    /**
     * Обновляет состояние карточки и пересчитывает прогноз/остаток.
     * Все вычисления — в памяти через ServiceUseCase.computeRemaining, БД не трогаем.
     */
    private fun publishItem(item: ServiceItem) {
        val remaining = useCase.computeRemaining(item)
        _state.value = _state.value.copy(
            item = item,
            isLoading = false,
            remainingText = useCase.formatRemaining(remaining),
            forecastText = buildForecast(item, remaining)
        )
    }

    /**
     * «Следующее ТО: ~125 000 км или 15.11.2026».
     * Берём оба целевых значения из RemainingInfo; если интервал по одному из измерений
     * не задан (0) — показываем только доступное.
     */
    private fun buildForecast(
        item: ServiceItem,
        remaining: ServiceUseCase.RemainingInfo
    ): String {
        val kmPart = if (remaining.nextDueKm > 0) {
            "~${useCase.formatNumber(remaining.nextDueKm)} км"
        } else null
        val datePart = if (remaining.nextDueDateMillis > 0) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                .format(Date(remaining.nextDueDateMillis))
        } else null
        return when {
            kmPart != null && datePart != null -> "Следующее ТО: $kmPart или $datePart"
            kmPart != null -> "Следующее ТО: $kmPart"
            datePart != null -> "Следующее ТО: $datePart"
            else -> ""
        }.let { text ->
            // Подсказка о просрочке — чтобы на экране деталей было видно текущее состояние.
            if (item.urgency == ServiceItem.URGENCY_RED) "$text · просрочено" else text
        }
    }

    fun markDone(currentKm: Int) {
        val current = _state.value.item ?: return
        viewModelScope.launch {
            try {
                val updated = useCase.markServiceDone(current, currentKm)
                repo.updateItem(updated)
                repo.insertRecord(
                    useCase.createRecord(
                        itemId = updated.id,
                        km = currentKm,
                        note = "Пробег: $currentKm км"
                    )
                )
                _state.value = _state.value.copy(item = updated)
                publishItem(updated)
            } catch (t: Throwable) {
                android.util.Log.e("AutoSense", "markDone failed", t)
            }
        }
    }

    fun editItem(newName: String, newIntervalKm: Int, newIntervalMonths: Int) {
        val current = _state.value.item ?: return
        viewModelScope.launch {
            try {
                val edited = current.copy(
                    name = newName,
                    intervalKm = newIntervalKm,
                    intervalMonths = newIntervalMonths
                )
                val withUrgency = edited.copy(
                    urgency = useCase.computeUrgency(
                        item = edited,
                        yellowThreshold = prefs.yellowThreshold
                    )
                )
                repo.updateItem(withUrgency)
                publishItem(withUrgency)
            } catch (t: Throwable) {
                android.util.Log.e("AutoSense", "editItem failed", t)
            }
        }
    }


    /**
     * Редактирование записи истории ТО: меняются дата, пробег, стоимость и заметка.
     * После успешного обновления Room список истории перезапустит свой Flow
     * (observeHistory(itemId)) — UI обновится автоматически.
     */
    fun editRecord(
        record: ServiceRecord,
        newDate: Long,
        newKm: Int,
        newCost: Int,
        newNote: String
    ) {
        viewModelScope.launch {
            try {
                val updated = record.copy(
                    dateMillis = newDate,
                    km = newKm,
                    cost = newCost,
                    note = newNote
                )
                repo.updateRecord(updated)
                _messages.emit(UiMessage.Info("Запись обновлена"))
            } catch (t: Throwable) {
                android.util.Log.e("AutoSense", "Failed to update record", t)
                _messages.emit(UiMessage.Error("Не удалось обновить запись"))
            }
        }
    }

    class Factory(
        private val app: Application,
        private val itemId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(app, itemId) as T
        }
    }
}