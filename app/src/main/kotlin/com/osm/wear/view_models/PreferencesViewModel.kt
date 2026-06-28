package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.services.INavigationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val preferencesRepository: IPreferencesRepository,
    private val navigationService: INavigationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.update { 
            it.copy(
                mapTheme = preferencesRepository.getMapTheme(),
                navigationAlertMode = preferencesRepository.getNavigationAlertMode(),
                navigationMode = preferencesRepository.getNavigationMode(),
                gpsBatteryMode = preferencesRepository.getGpsBatteryMode()
            ) 
        }
    }

    fun setMapTheme(theme: MapTheme) {
        _uiState.update { it.copy(mapTheme = theme) }
        preferencesRepository.setMapTheme(theme)
    }

    fun setNavigationAlertMode(mode: NavigationAlertMode) {
        _uiState.update { it.copy(navigationAlertMode = mode) }
        navigationService.setAlertMode(mode)
        preferencesRepository.setNavigationAlertMode(mode)
    }

    fun setGpsBatteryMode(mode: GpsBatteryMode) {
        _uiState.update { it.copy(gpsBatteryMode = mode) }
        preferencesRepository.setGpsBatteryMode(mode)
    }

    fun cycleNavigationMode() {
        val nextMode = when (_uiState.value.navigationMode) {
            NavigationMode.WALKING -> NavigationMode.CYCLING
            NavigationMode.CYCLING -> NavigationMode.DRIVING
            NavigationMode.DRIVING -> NavigationMode.GPX_ONLY
            NavigationMode.GPX_ONLY -> NavigationMode.WALKING
        }
        _uiState.update { it.copy(navigationMode = nextMode) }
        preferencesRepository.setNavigationMode(nextMode)
    }
}
