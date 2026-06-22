package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpsBatteryMode
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.MapRotationMode
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.ILocationRepository
import com.osm.wear.repositories.ISettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepo: ILocationRepository,
    private val settingsRepository: ISettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    private var locationJob: Job? = null

    init {
        loadMapState()
    }

    private fun loadMapState() {
        val lat = settingsRepository.getMapCenterLat()
        val lon = settingsRepository.getMapCenterLon()
        val zoom = settingsRepository.getMapZoomLevel()
        val follow = settingsRepository.getMapFollowLocation()
        val tappedPoint = settingsRepository.getTappedPoint()

        _uiState.update { 
            it.copy(
                centerLat = lat, 
                centerLon = lon, 
                zoomLevel = zoom, 
                followLocation = follow,
                tappedPoint = tappedPoint
            ) 
        }

        if (lat == 0.0 && lon == 0.0) {
            centerOnLocation()
        }
    }

    private fun persistMapState() {
        val state = _uiState.value
        settingsRepository.setMapCenterLat(state.centerLat)
        settingsRepository.setMapCenterLon(state.centerLon)
        settingsRepository.setMapZoomLevel(state.zoomLevel)
        settingsRepository.setMapFollowLocation(state.followLocation)
        settingsRepository.setTappedPoint(state.tappedPoint)
    }

    fun startLocationTracking(batteryMode: GpsBatteryMode) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepo.locationFlow(batteryMode).collect { loc ->
                _currentLocation.value = loc
                if (_uiState.value.followLocation) {
                    _uiState.update { it.copy(centerLat = loc.latitude, centerLon = loc.longitude) }
                    persistMapState()
                }
            }
        }
    }

    fun centerOnLocation() {
        val currentFollow = _uiState.value.followLocation
        if (!currentFollow) {
            _uiState.update { 
                it.copy(
                    followLocation = true,
                    mapRotationMode = MapRotationMode.NORTH_UP
                ) 
            }
        } else {
            val nextMode = if (_uiState.value.mapRotationMode == MapRotationMode.NORTH_UP) {
                MapRotationMode.HEADING_UP
            } else {
                MapRotationMode.NORTH_UP
            }
            _uiState.update { it.copy(mapRotationMode = nextMode) }
        }
        persistMapState()
        _currentLocation.value?.let { loc ->
            _uiState.update {
                it.copy(
                    centerLat = loc.latitude,
                    centerLon = loc.longitude
                )
            }
        } ?: run {
            viewModelScope.launch {
                val lastLoc = locationRepo.getLastKnownLocation()
                lastLoc?.let { loc ->
                    _currentLocation.value = loc
                    if (_uiState.value.followLocation) {
                        _uiState.update {
                            it.copy(
                                centerLat = loc.latitude,
                                centerLon = loc.longitude
                            )
                        }
                    }
                }
            }
        }
    }

    fun stopFollowingLocation() {
        _uiState.update { it.copy(followLocation = false) }
        persistMapState()
    }

    fun zoomIn() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel + 1).coerceAtMost(20)) }
        persistMapState()
    }

    fun zoomOut() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel - 1).coerceAtLeast(3)) }
        persistMapState()
    }

    fun onMapPanned(newLat: Double, newLon: Double) {
        _uiState.update { 
            it.copy(
                centerLat = newLat, 
                centerLon = newLon, 
                followLocation = false,
                mapRotationMode = if (it.mapRotationMode == MapRotationMode.HEADING_UP) MapRotationMode.NORTH_UP else it.mapRotationMode
            ) 
        }
        persistMapState()
    }

    fun onMapRotated(rotation: Float) {
        _uiState.update { 
            it.copy(
                mapRotationMode = MapRotationMode.MANUAL,
                manualRotation = rotation,
                followLocation = false
            ) 
        }
        persistMapState()
    }

    fun onMapTapped(lat: Double, lon: Double) {
        val pt = GpxPoint(lat, lon)
        _uiState.update { it.copy(tappedPoint = pt) }
        persistMapState()
    }

    fun clearTappedPoint() {
        _uiState.update { it.copy(tappedPoint = null) }
        persistMapState()
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}
