package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.repositories.ISettingsRepository
import com.osm.wear.services.INavigationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: ISettingsRepository,
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
                mapTheme = settingsRepository.getMapTheme(),
                navigationAlertMode = settingsRepository.getNavigationAlertMode(),
                navigationMode = settingsRepository.getNavigationMode()
            ) 
        }
    }

    fun setMapTheme(theme: MapTheme) {
        _uiState.update { it.copy(mapTheme = theme) }
        settingsRepository.setMapTheme(theme)
    }

    fun setNavigationAlertMode(mode: NavigationAlertMode) {
        _uiState.update { it.copy(navigationAlertMode = mode) }
        navigationService.setAlertMode(mode)
        settingsRepository.setNavigationAlertMode(mode)
    }

    fun setGpsBatteryMode(mode: GpsBatteryMode) {
        _uiState.update { it.copy(gpsBatteryMode = mode) }
    }

    fun cycleNavigationMode() {
        val nextMode = when (_uiState.value.navigationMode) {
            NavigationMode.WALKING -> NavigationMode.CYCLING
            NavigationMode.CYCLING -> NavigationMode.DRIVING
            NavigationMode.DRIVING -> NavigationMode.WALKING
        }
        _uiState.update { it.copy(navigationMode = nextMode) }
        settingsRepository.setNavigationMode(nextMode)
    }
}

