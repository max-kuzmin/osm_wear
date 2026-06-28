package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.enums.MapRotationMode
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.repositories.IRegionRepository
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.services.IMarkerService
import com.osm.wear.services.INavigationTrackingService
import com.osm.wear.models.Bookmark
import com.osm.wear.models.NavigationState
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.GpxFile
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MapViewModel @Inject constructor(
    private val cursorRepository: ICursorRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val markerService: IMarkerService,
    private val navigationTrackingService: INavigationTrackingService,
    private val gpxRepository: IGpxRepository,
    private val regionRepository: IRegionRepository,
    private val geocodingRepository: IGeocodingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    val activeMapFile: StateFlow<File?> = regionRepository.activeMapFile
    val activeGpxFile: StateFlow<GpxFile?> = gpxRepository.activeGpxFile
    val navigationState: StateFlow<NavigationState?> = navigationTrackingService.navigationState

    val markerState: StateFlow<MarkerUiState> = uiState
        .map { mapState ->
            MarkerUiState(
                tappedPoint = mapState.tappedPoint,
                tappedPointName = mapState.tappedPointName,
                tappedPointAddress = mapState.tappedPointAddress,
                isResolvingAddress = mapState.isResolvingAddress
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarkerUiState())

    private var locationJob: Job? = null

    init {
        loadMapState()
        loadMarker()
    }

    fun loadMapState() {
        val center = preferencesRepository.getMapCenter()
        val lat = center.lat
        val lon = center.lon
        val zoom = preferencesRepository.getMapZoomLevel()
        val follow = preferencesRepository.getMapFollowLocation()
        val theme = preferencesRepository.getMapTheme()

        _uiState.update {
            it.copy(
                centerLat = lat,
                centerLon = lon,
                zoomLevel = zoom,
                followLocation = follow,
                mapTheme = theme
            )
        }

        if (lat == 0.0 && lon == 0.0) {
            centerOnLocation()
        }
        
        startLocationTracking(preferencesRepository.getGpsBatteryMode())
    }

    private fun persistMapState() {
        val state = _uiState.value
        preferencesRepository.setMapCenter(state.centerLat, state.centerLon)
        preferencesRepository.setMapZoomLevel(state.zoomLevel)
        preferencesRepository.setMapFollowLocation(state.followLocation)
    }

    fun startLocationTracking(batteryMode: GpsBatteryMode) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            cursorRepository.locationFlow(batteryMode).collect { loc ->
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
                val lastLoc = cursorRepository.getLastKnownLocation()
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

    private val _centerEvents = MutableSharedFlow<LatLong>(extraBufferCapacity = 1)
    val centerEvents: SharedFlow<LatLong> = _centerEvents.asSharedFlow()

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

    private fun loadMarker() {
        viewModelScope.launch {
            markerService.currentMarker.collect { marker ->
                val matchingBookmark = if (marker != null) {
                    markerService.bookmarks.value.find {
                        it.lat == marker.lat && it.lon == marker.lon
                    }
                } else null

                _uiState.update {
                    it.copy(
                        tappedPoint = marker,
                        tappedPointName = matchingBookmark?.name,
                        tappedPointAddress = matchingBookmark?.address
                    )
                }

                if (marker != null && matchingBookmark == null) {
                    resolveAddressForPoint(marker)
                }
            }
        }
    }

    fun onMapTapped(lat: Double, lon: Double) {
        val pt = GpxPoint(lat, lon)
        markerService.setCurrentMarker(pt)
    }

    private fun resolveAddressForPoint(pt: GpxPoint, overrideName: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isResolvingAddress = true) }
                val result = geocodingRepository.reverseGeocode(pt.lat, pt.lon)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            tappedPointName = overrideName ?: result.name,
                            tappedPointAddress = result.address,
                            isResolvingAddress = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isResolvingAddress = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isResolvingAddress = false) }
            }
        }
    }
}
