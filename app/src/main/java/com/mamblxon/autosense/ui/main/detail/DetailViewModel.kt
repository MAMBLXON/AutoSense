package com.mamblxon.autosense.ui.main.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.domain.usecase.AdaptiveIntervalCalculator
import com.mamblxon.autosense.domain.usecase.AdaptiveResult
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.prefs.AppPrefs
import com.mamblxon.autosense.ui.main.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: ServiceItem? = null,
    val history: List<ServiceRecord> = emptyList(),
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val adaptive: AdaptiveResult? = null
)

class DetailViewModel(
    app: Application,
    private val itemId: Long
) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val useCase = ServiceUseCase()
    private val prefs = AppPrefs(app)
    private val adaptiveCalc = AdaptiveIntervalCalculator()

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val item = repo.getItemById(itemId)
                if (item == null) {
                    _state.value = _state.value.copy(notFound = true, isLoading = false)
                    return@launch
                }
                val history = repo.observeHistory(itemId).first()
                val adaptive = if (prefs.adaptiveModeEnabled) {
                    adaptiveCalc.compute(item.intervalKm, item.intervalMonths, history)
                } else {
                    AdaptiveResult(item.intervalKm, item.intervalMonths, false, "Адаптивный режим отключён в настройках.")
                }
                _state.value = DetailUiState(
                    item = item,
                    history = history,
                    isLoading = false,
                    notFound = false,
                    adaptive = adaptive
                )
            } catch (t: Throwable) {
                Log.e("AutoSense", "Failed to load detail", t)
                _state.value = _state.value.copy(isLoading = false)
                _messages.emit(UiMessage.Error("Ошибка загрузки данных"))
            }
        }
    }

    fun markDone(km: Int, cost: Int, note: String, updateCarKm: Boolean) {
        viewModelScope.launch {
            try {
                val item = _state.value.item ?: return@launch
                if (updateCarKm) {
                    prefs.saveKnownKm(prefs.currentProfileId, km)
                }
                val updatedItem = useCase.markServiceDone(item, km)
                repo.updateItem(updatedItem)
                val record = useCase.createRecord(item.id, km, note = note, cost = cost)
                repo.insertRecord(record)
                _messages.emit(UiMessage.Info("ТО отмечено"))
                loadData()
            } catch (t: Throwable) {
                Log.e("AutoSense", "Failed to mark done", t)
                _messages.emit(UiMessage.Error("Ошибка сохранения"))
            }
        }
    }

    fun editItem(name: String, intervalKm: Int, intervalMonths: Int) {
        viewModelScope.launch {
            try {
                val item = _state.value.item ?: return@launch
                val updated = item.copy(name = name, intervalKm = intervalKm, intervalMonths = intervalMonths)
                repo.updateItem(updated)
                _state.value = _state.value.copy(item = updated)
                _messages.emit(UiMessage.Info("Сохранено"))
            } catch (t: Throwable) {
                Log.e("AutoSense", "Failed to edit item", t)
                _messages.emit(UiMessage.Error("Ошибка сохранения"))
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
