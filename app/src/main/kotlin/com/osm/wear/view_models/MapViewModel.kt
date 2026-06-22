package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpsBatteryMode
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

        _uiState.update {
            it.copy(
                centerLat = lat,
                centerLon = lon,
                zoomLevel = zoom,
                followLocation = follow
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
                followLocation = false
            )
        }
        persistMapState()
    }

    private var isRotating = false
    private var previousPinchAngle = 0f

    fun onPinchDown(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        previousPinchAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        isRotating = true
    }

    fun onPinchMove(x1: Float, y1: Float, x2: Float, y2: Float, currentRot: Float): Float? {
        if (!isRotating) return null
        val dx = x2 - x1
        val dy = y2 - y1
        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val delta = angle - previousPinchAngle
        previousPinchAngle = angle
        val newRot = currentRot + delta

        _uiState.update {
            it.copy(
                mapRotationMode = MapRotationMode.MANUAL,
                manualRotation = newRot,
                followLocation = false
            )
        }
        persistMapState()
        return newRot
    }

    fun getMapPivot(width: Int, height: Int): Pair<Float, Float> {
        val pivotX = if (width > 0) width * 0.5f else 0f
        val pivotY = if (height > 0) height * 0.5f else 0f
        return Pair(pivotX, pivotY)
    }

    fun getUnrotatedTapPoint(x: Float, y: Float, width: Int, height: Int, rotation: Float): Pair<Double, Double> {
        val (pivotX, pivotY) = getMapPivot(width, height)
        val angleRad = Math.toRadians(-rotation.toDouble())

        val dx = x.toDouble() - pivotX
        val dy = y.toDouble() - pivotY

        val rx = dx * Math.cos(angleRad) - dy * Math.sin(angleRad)
        val ry = dx * Math.sin(angleRad) + dy * Math.cos(angleRad)

        return Pair(rx + pivotX, ry + pivotY)
    }

    fun onPinchUp() {
        isRotating = false
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}
