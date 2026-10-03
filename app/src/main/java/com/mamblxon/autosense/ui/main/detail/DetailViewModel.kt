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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: ServiceItem? = null,
    val history: List<ServiceRecord> = emptyList(),
    val isLoading: Boolean = true,
    val notFound: Boolean = false
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
            _state.value = _state.value.copy(item = item, isLoading = false)

            repo.observeHistory(itemId).collect { list ->
                _state.value = _state.value.copy(history = list)
            }
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
                _state.value = _state.value.copy(item = withUrgency)
            } catch (t: Throwable) {
                android.util.Log.e("AutoSense", "editItem failed", t)
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