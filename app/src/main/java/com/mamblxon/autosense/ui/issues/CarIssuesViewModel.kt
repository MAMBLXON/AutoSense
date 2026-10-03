package com.mamblxon.autosense.ui.issues

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.data.remote.CarIssue
import com.mamblxon.autosense.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class IssueFilter {
    ALL, CRITICAL, WARNING, INFO
}

data class CarIssuesUiState(
    val carDisplayName: String = "",
    val allIssues: List<CarIssue> = emptyList(),
    val visibleIssues: List<CarIssue> = emptyList(),
    val filter: IssueFilter = IssueFilter.ALL
)

class CarIssuesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(CarIssuesUiState())
    val state: StateFlow<CarIssuesUiState> = _state.asStateFlow()

    private var allIssues: List<CarIssue> = emptyList()

    init {
        viewModelScope.launch {
            try {
                val profileId = prefs.currentProfileId
                if (profileId <= 0L) {
                    _state.value = _state.value.copy(
                        carDisplayName = "Профиль не выбран",
                        allIssues = emptyList(),
                        visibleIssues = emptyList()
                    )
                    return@launch
                }

                val profile = repo.getProfileById(profileId)
                if (profile == null) {
                    _state.value = _state.value.copy(
                        carDisplayName = "Профиль не найден",
                        allIssues = emptyList(),
                        visibleIssues = emptyList()
                    )
                    return@launch
                }

                val catalog = repo.loadCatalog()
                val car = catalog.cars.firstOrNull { it.id == profile.catalogId }

                allIssues = car?.issues.orEmpty()
                _state.value = _state.value.copy(
                    carDisplayName = car?.displayName ?: profile.catalogId,
                    allIssues = allIssues,
                    visibleIssues = applyFilter(allIssues, _state.value.filter)
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    carDisplayName = "Ошибка загрузки",
                    allIssues = emptyList(),
                    visibleIssues = emptyList()
                )
            }
        }
    }

    fun setFilter(filter: IssueFilter) {
        _state.value = _state.value.copy(
            filter = filter,
            visibleIssues = applyFilter(allIssues, filter)
        )
    }

    private fun applyFilter(issues: List<CarIssue>, filter: IssueFilter): List<CarIssue> =
        when (filter) {
            IssueFilter.ALL -> issues
            IssueFilter.CRITICAL -> issues.filter { it.severity == "critical" }
            IssueFilter.WARNING -> issues.filter { it.severity == "warning" }
            IssueFilter.INFO -> issues.filter { it.severity == "info" }
        }
}