package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.enums.MapRotationMode
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.repositories.IRegionRepository
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IBillingRepository
import com.osm.wear.services.INavigationTrackingService
import com.osm.wear.services.IRegionValidatorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val cursorRepository: ICursorRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val markersRepository: IMarkersRepository,
    private val navigationTrackingService: INavigationTrackingService,
    private val gpxRepository: IGpxRepository,
    private val regionRepository: IRegionRepository,
    private val geocodingRepository: IGeocodingRepository,
    private val billingRepository: IBillingRepository,
    private val regionValidatorService: IRegionValidatorService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _effect = Channel<MapEffect>(Channel.BUFFERED)
    val effect: Flow<MapEffect> = _effect.receiveAsFlow()

    private var locationJob: Job? = null

    init {
        observeRepositories()
    }

    private fun observeRepositories() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                regionRepository.activeRegionId,
                regionRepository.activeMapFile,
                billingRepository.purchasedProductIds
            ) { regionId, file, purchasedIds ->
                if (regionId != null && file != null) {
                    if (!regionValidatorService.isRegionValid(regionId, purchasedIds)) {
                        regionRepository.setActiveRegionId(null)
                        _effect.send(MapEffect.ShowToast("Please choose a map region in settings"))
                        _uiState.update { it.copy(activeMapFile = null) }
                    } else {
                        _uiState.update { it.copy(activeMapFile = file) }
                    }
                } else {
                    _uiState.update { it.copy(activeMapFile = null) }
                    
                    // Delay check slightly to prevent toast spam on very first open before things load,
                    // but the combine block runs when these flow emit.
                    // Let's only toast if there's no active file.
                    if (file == null) {
                        _effect.send(MapEffect.ShowToast("Please choose a map region in settings"))
                    }
                }
            }.collect { }
        }
        viewModelScope.launch {
            gpxRepository.activeGpxFile.collect { file ->
                _uiState.update { it.copy(activeGpxFile = file) }
            }
        }
        viewModelScope.launch {
            navigationTrackingService.navigationState.collect { state ->
                _uiState.update { it.copy(navigationState = state) }
            }
        }
        viewModelScope.launch {
            markersRepository.currentMarker.collect { marker ->
                val matchingBookmark = if (marker != null) {
                    markersRepository.bookmarks.value.find {
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

    fun onIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.LoadMapState -> loadMapState()
            is MapIntent.StartLocationTracking -> startLocationTracking(intent.batteryMode)
            is MapIntent.CenterOnLocation -> centerOnLocation()
            is MapIntent.ZoomIn -> zoomIn()
            is MapIntent.ZoomOut -> zoomOut()
            is MapIntent.MapPanned -> onMapPanned(intent.lat, intent.lon)
            is MapIntent.PinchMoved -> onPinchMoved(intent.newRotation)
            is MapIntent.MapTapped -> onMapTapped(intent.lat, intent.lon)
        }
    }

    private fun loadMapState() {
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

    private fun startLocationTracking(batteryMode: GpsBatteryMode) {
        if (!cursorRepository.isGpsEnabled()) {
            return
        }
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            cursorRepository.locationFlow(batteryMode).collect { loc ->
                _uiState.update { it.copy(currentLocation = loc) }
                if (_uiState.value.followLocation) {
                    _uiState.update { it.copy(centerLat = loc.latitude, centerLon = loc.longitude) }
                    persistMapState()
                }
            }
        }
    }

    private fun centerOnLocation() {
        if (!cursorRepository.isGpsEnabled()) {
            viewModelScope.launch { _effect.send(MapEffect.ShowToast("GPS is disabled")) }
            return
        }
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
        
        _uiState.value.currentLocation?.let { loc ->
            _uiState.update {
                it.copy(
                    centerLat = loc.latitude,
                    centerLon = loc.longitude
                )
            }
            viewModelScope.launch { _effect.send(MapEffect.CenterMap(LatLong(loc.latitude, loc.longitude))) }
        } ?: run {
            viewModelScope.launch {
                val lastLoc = cursorRepository.getLastKnownLocation()
                lastLoc?.let { loc ->
                    _uiState.update { it.copy(currentLocation = loc) }
                    if (_uiState.value.followLocation) {
                        _uiState.update {
                            it.copy(
                                centerLat = loc.latitude,
                                centerLon = loc.longitude
                            )
                        }
                        _effect.send(MapEffect.CenterMap(LatLong(loc.latitude, loc.longitude)))
                    }
                }
            }
        }
    }

    private fun zoomIn() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel + 1).coerceAtMost(20)) }
        persistMapState()
    }

    private fun zoomOut() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel - 1).coerceAtLeast(3)) }
        persistMapState()
    }

    private fun onMapPanned(newLat: Double, newLon: Double) {
        _uiState.update {
            it.copy(
                centerLat = newLat,
                centerLon = newLon,
                followLocation = false
            )
        }
        persistMapState()
    }

    private fun onPinchMoved(newRot: Float) {
        _uiState.update {
            it.copy(
                mapRotationMode = MapRotationMode.MANUAL,
                manualRotation = newRot,
                followLocation = false
            )
        }
        persistMapState()
    }

    private fun onMapTapped(lat: Double, lon: Double) {
        val pt = GpxPoint(lat, lon)
        markersRepository.setCurrentMarker(pt)
    }

    private suspend fun resolveAddressForPoint(pt: GpxPoint, overrideName: String? = null) {
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

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        _effect.close()
    }
}
