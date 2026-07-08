package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpxFile
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.services.INavigationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IRegionRepository

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    private val gpxRepository: IGpxRepository,
    private val navigationTrackingService: INavigationTrackingService,
    private val cursorRepository: ICursorRepository,
    private val regionRepository: IRegionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainMenuUiState())
    val uiState: StateFlow<MainMenuUiState> = _uiState.asStateFlow()

    private val _effect = Channel<MainMenuEffect>(Channel.BUFFERED)
    val effect: Flow<MainMenuEffect> = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                gpxRepository.activeGpxFile,
                navigationTrackingService.navigationState,
                regionRepository.activeRegionId
            ) { activeGpx, navState, activeRegionId ->
                val activeRegion = activeRegionId?.let { id -> regionRepository.all.find { it.id == id } }
                MainMenuUiState(activeGpx, navState, activeRegion)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onIntent(intent: MainMenuIntent) {
        when (intent) {
            is MainMenuIntent.StartNavigation -> startNavigation(intent.gpx, intent.initialLocation)
            is MainMenuIntent.StopNavigation -> stopNavigation()
        }
    }

    private fun startNavigation(gpx: GpxFile, initialLocation: UserLocation?) {
        if (!cursorRepository.isGpsEnabled()) {
            viewModelScope.launch { _effect.send(MainMenuEffect.ShowToast("GPS is disabled")) }
            return
        }
        viewModelScope.launch {
            val loc = initialLocation ?: cursorRepository.getLastKnownLocation()
            val error = navigationTrackingService.startNavigation(gpx, loc)
            if (error != null) {
                _effect.send(MainMenuEffect.ShowToast(error))
            } else {
                _effect.send(MainMenuEffect.ShowMap)
            }
        }
    }

    private fun stopNavigation() {
        navigationTrackingService.stopNavigation()
    }

    override fun onCleared() {
        super.onCleared()
        _effect.close()
    }
}
