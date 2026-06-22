package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpsBatteryMode
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.MapRotationMode
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.ILocationRepository
import com.osm.wear.repositories.ISettingsRepository
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IBookmarkRepository
import com.osm.wear.models.Bookmark
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
    private val settingsRepository: ISettingsRepository,
    private val geocodingRepository: IGeocodingRepository,
    private val bookmarkRepository: IBookmarkRepository
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

        val matchingBookmark = if (tappedPoint != null) {
            bookmarkRepository.bookmarks.value.find { it.lat == tappedPoint.lat && it.lon == tappedPoint.lon }
        } else null

        _uiState.update { 
            it.copy(
                centerLat = lat, 
                centerLon = lon, 
                zoomLevel = zoom, 
                followLocation = follow,
                tappedPoint = tappedPoint,
                tappedPointName = matchingBookmark?.name,
                tappedPointAddress = matchingBookmark?.address
            ) 
        }

        if (tappedPoint != null && matchingBookmark == null) {
            resolveAddressForPoint(tappedPoint)
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

    fun onMapTapped(lat: Double, lon: Double) {
        val pt = GpxPoint(lat, lon)
        _uiState.update {
            it.copy(
                tappedPoint = pt,
                tappedPointName = null,
                tappedPointAddress = null,
                isResolvingAddress = true
            )
        }
        persistMapState()
        resolveAddressForPoint(pt)
    }

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.bookmarks

    fun saveBookmarkFromMap(pt: GpxPoint, resolvedName: String?, resolvedAddress: String? = null) {
        viewModelScope.launch {
            var name = resolvedName
            var address = resolvedAddress
            
            if (name == null || address == null) {
                try {
                    val res = geocodingRepository.reverseGeocode(pt.lat, pt.lon)
                    if (res != null) {
                        if (name == null) name = res.name
                        if (address == null) address = res.address
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            val finalName = name ?: "Point (%.4f, %.4f)".format(pt.lat, pt.lon)

            bookmarkRepository.addBookmark(
                Bookmark(
                    name = finalName,
                    address = address,
                    lat = pt.lat,
                    lon = pt.lon
                )
            )
        }
    }

    fun saveSearchBookmark(name: String, address: String?, lat: Double, lon: Double) {
        bookmarkRepository.addBookmark(
            Bookmark(
                name = name,
                address = address,
                lat = lat,
                lon = lon
            )
        )
    }

    fun deleteBookmark(bookmark: Bookmark) {
        bookmarkRepository.removeBookmark(bookmark)
    }

    fun selectBookmark(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        _uiState.update {
            it.copy(
                tappedPoint = pt,
                tappedPointName = bookmark.name,
                tappedPointAddress = null,
                isResolvingAddress = true
            )
        }
        persistMapState()
        resolveAddressForPoint(pt, overrideName = bookmark.name)
    }

    private val _searchResults = MutableStateFlow<List<com.osm.wear.repositories.GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<com.osm.wear.repositories.GeocodeResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchAddresses(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        viewModelScope.launch {
            try {
                val results = geocodingRepository.searchAddress(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun onPinchUp() {
        isRotating = false
    }

    fun clearTappedPoint() {
        _uiState.update { it.copy(tappedPoint = null) }
        persistMapState()
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

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}
