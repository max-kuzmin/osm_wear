package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.UserLocation
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.services.BuildRouteToMarkerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarkersViewModel @Inject constructor(
    private val markersRepository: IMarkersRepository,
    private val cursorRepository: ICursorRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val gpxRepo: IGpxRepository,
    private val geocodingRepository: IGeocodingRepository,
    private val buildRouteToMarkerUseCase: BuildRouteToMarkerUseCase
) : ViewModel() {

    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    val currentMarker: StateFlow<GpxPoint?> = markersRepository.currentMarker

    val bookmarks: StateFlow<List<Bookmark>> = markersRepository.bookmarks

    // Combine bookmarks and location to compute distance to markers
    val bookmarkDistances: StateFlow<List<Pair<Bookmark, Double?>>> = combine(
        markersRepository.bookmarks,
        _currentLocation
    ) { bList, loc ->
        bList.map { b ->
            val dist = loc?.let {
                distanceMeters(it.latitude, it.longitude, b.lat, b.lon)
            }
            Pair(b, dist)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            cursorRepository.locationFlow(GpsBatteryMode.BALANCED).collect { loc ->
                _currentLocation.value = loc
            }
        }
    }

    fun selectBookmark(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        markersRepository.setCurrentMarker(pt)
        preferencesRepository.setMapCenter(bookmark.lat, bookmark.lon)
        preferencesRepository.setMapFollowLocation(false)
    }

    fun saveBookmarkFromMap(pt: GpxPoint) {
        viewModelScope.launch {
            val res = geocodingRepository.reverseGeocode(pt.lat, pt.lon)
            val finalName = res?.name ?: "Point (%.4f, %.4f)".format(pt.lat, pt.lon)
            markersRepository.addBookmark(
                Bookmark(
                    name = finalName,
                    address = res?.address,
                    lat = pt.lat,
                    lon = pt.lon
                )
            )
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        markersRepository.removeBookmark(bookmark)
    }

    fun buildRouteToPoint(
        target: GpxPoint,
        onRouteBuilt: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            buildRouteToMarkerUseCase(target, currentLocation.value)
                .onSuccess { gpx ->
                    gpxRepo.saveGpxFile("Path Finder", gpx.trackPoints)
                        .onSuccess { savedGpx ->
                            gpxRepo.setActive(savedGpx.id)
                            onRouteBuilt()
                        }
                        .onFailure { err ->
                            onFailure(err.message ?: "Failed to save route")
                        }
                }
                .onFailure { err ->
                    onFailure(err.message ?: "Routing failed. Check your internet connection.")
                }
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
