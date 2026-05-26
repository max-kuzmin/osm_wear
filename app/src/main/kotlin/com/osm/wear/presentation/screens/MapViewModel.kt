package com.osm.wear.presentation.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.data.gpx.GpxRepository
import com.osm.wear.data.location.LocationRepository
import com.osm.wear.data.map.MapDownloadManager
import com.osm.wear.data.map.MapRegionCatalog
import com.osm.wear.domain.model.DownloadProgress
import com.osm.wear.domain.model.GpxTrack
import com.osm.wear.domain.model.MapRegion
import com.osm.wear.domain.model.RegionStatus
import com.osm.wear.domain.model.UserLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val locationRepo = LocationRepository(context)
    val downloadManager = MapDownloadManager(context)
    val gpxRepo = GpxRepository(context)

    // ── Location ───────────────────────────────────────────────────────────────
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(false)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    // ── Map state ──────────────────────────────────────────────────────────────
    private val _activeMapFile = MutableStateFlow<File?>(null)
    val activeMapFile: StateFlow<File?> = _activeMapFile.asStateFlow()

    private val _zoomLevel = MutableStateFlow(14)
    val zoomLevel: StateFlow<Int> = _zoomLevel.asStateFlow()

    private val _centerLat = MutableStateFlow(52.52) // Default: Berlin
    private val _centerLon = MutableStateFlow(13.405)
    val centerLat: StateFlow<Double> = _centerLat.asStateFlow()
    val centerLon: StateFlow<Double> = _centerLon.asStateFlow()

    // ── Download state ─────────────────────────────────────────────────────────
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> =
        downloadManager.activeDownloads.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _downloadedRegionIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedRegionIds: StateFlow<Set<String>> = _downloadedRegionIds.asStateFlow()

    // ── GPX state ──────────────────────────────────────────────────────────────
    val gpxTracks: StateFlow<List<GpxTrack>> = gpxRepo.tracks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refreshDownloadedRegions()
        loadFirstAvailableMap()
    }

    // ── Location ───────────────────────────────────────────────────────────────

    fun startLocationTracking() {
        if (_isTrackingLocation.value) return
        _isTrackingLocation.value = true
        viewModelScope.launch {
            locationRepo.locationUpdates().collect { location ->
                _userLocation.value = location
            }
        }
    }

    fun stopLocationTracking() {
        _isTrackingLocation.value = false
        // Flow will be cancelled when viewModelScope is cleared
    }

    fun centerOnUserLocation() {
        _userLocation.value?.let { loc ->
            _centerLat.value = loc.latitude
            _centerLon.value = loc.longitude
        }
    }

    // ── Map controls ───────────────────────────────────────────────────────────

    fun zoomIn() {
        _zoomLevel.value = (_zoomLevel.value + 1).coerceAtMost(20)
    }

    fun zoomOut() {
        _zoomLevel.value = (_zoomLevel.value - 1).coerceAtLeast(3)
    }

    fun setCenter(lat: Double, lon: Double) {
        _centerLat.value = lat
        _centerLon.value = lon
    }

    fun setActiveMapFile(file: File?) {
        _activeMapFile.value = file
    }

    // ── Downloads ──────────────────────────────────────────────────────────────

    fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.downloadRegion(region).collect { progress ->
                if (progress.status == RegionStatus.DOWNLOADED) {
                    refreshDownloadedRegions()
                    // Auto-load if no map is active
                    if (_activeMapFile.value == null) {
                        loadFirstAvailableMap()
                    }
                }
            }
        }
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.deleteRegion(region)
            refreshDownloadedRegions()
            // If this was the active map, clear it
            val activeFile = _activeMapFile.value
            if (activeFile != null && !activeFile.exists()) {
                _activeMapFile.value = null
                loadFirstAvailableMap()
            }
        }
    }

    fun selectRegionAsActiveMap(region: MapRegion) {
        val file = downloadManager.getLocalMapFile(region)
        _activeMapFile.value = file
    }

    private fun refreshDownloadedRegions() {
        val ids = downloadManager.getDownloadedRegionIds().toSet()
        _downloadedRegionIds.value = ids
    }

    private fun loadFirstAvailableMap() {
        val ids = downloadManager.getDownloadedRegionIds()
        if (ids.isNotEmpty()) {
            val region = MapRegionCatalog.findById(ids.first())
                ?: MapRegion(ids.first(), ids.first(), "", "", 0L)
            val file = downloadManager.getLocalMapFile(region)
            _activeMapFile.value = file
        }
    }

    // ── GPX ────────────────────────────────────────────────────────────────────

    fun importGpxFromUri(uri: Uri) {
        viewModelScope.launch {
            gpxRepo.importFromUri(uri)
        }
    }

    fun deleteGpxTrack(trackId: String) {
        viewModelScope.launch {
            gpxRepo.deleteTrack(trackId)
        }
    }

    fun toggleGpxTrackVisibility(trackId: String) {
        val track = gpxTracks.value.find { it.id == trackId } ?: return
        gpxRepo.setTrackVisibility(trackId, !track.isVisible)
    }
}
