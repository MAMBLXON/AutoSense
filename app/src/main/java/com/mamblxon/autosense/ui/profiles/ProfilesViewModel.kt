package com.mamblxon.autosense.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.CarProfile
import com.mamblxon.autosense.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProfileDisplay(
    val profile: CarProfile,
    val displayName: String,
    val subtitle: String,
    val isCurrent: Boolean
)

data class ProfilesUiState(
    val profiles: List<ProfileDisplay> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
)

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DefaultServiceRepository.get(app)
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val catalog = repo.loadCatalog()
                val carMap = catalog.cars.associateBy { it.id }

                repo.observeProfiles().collect { profiles ->
                    val currentId = prefs.currentProfileId
                    val displays = profiles.map { p ->
                        val model = carMap[p.catalogId]
                        ProfileDisplay(
                            profile = p,
                            displayName = model?.displayName ?: p.catalogId,
                            subtitle = buildString {
                                append("Год: ").append(p.year)
                                append(" · Двигатель: ").append(p.engineCode)
                                append(" · ").append(p.transmission)
                                if (p.currentKm > 0) {
                                    append(" · ").append(p.currentKm).append(" км")
                                }
                            },
                            isCurrent = p.id == currentId
                        )
                    }
                    _state.value = ProfilesUiState(
                        profiles = displays,
                        isLoading = false,
                        isEmpty = displays.isEmpty()
                    )
                }
            } catch (t: Throwable) {
                _state.value = ProfilesUiState(isLoading = false, isEmpty = true)
            }
        }
    }

    fun select(profile: CarProfile) {
        prefs.currentProfileId = profile.id
    }

    fun delete(profile: CarProfile, onDone: (needRestart: Boolean) -> Unit) {
        viewModelScope.launch {
            val wasCurrent = profile.id == prefs.currentProfileId
            repo.clearItems(profile.id)
            repo.deleteProfile(profile)

            if (wasCurrent) {
                val remaining = repo.observeProfiles().first()
                prefs.currentProfileId = remaining.firstOrNull()?.id ?: 0L
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }
}