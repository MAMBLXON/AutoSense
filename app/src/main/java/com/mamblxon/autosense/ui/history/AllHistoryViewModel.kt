package com.mamblxon.autosense.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceRecordWithName
import com.mamblxon.autosense.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AllHistoryUiState(
    val records: List<ServiceRecordWithName> = emptyList(),
    val totalCost: Int = 0,
    val count: Int = 0,
    val car: String = ""
)

class AllHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(AllHistoryUiState())
    val state: StateFlow<AllHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profileId = prefs.currentProfileId
            if (profileId <= 0L) return@launch

            val profile = repo.getProfileById(profileId) ?: return@launch
            val catalogId = profile.catalogId

            combine(
                repo.observeRecordsByCar(catalogId),
                repo.observeTotalCost(catalogId)
            ) { records, total ->
                AllHistoryUiState(
                    records = records,
                    totalCost = total ?: 0,
                    count = records.size,
                    car = catalogId
                )
            }.collect { _state.value = it }
        }
    }
}