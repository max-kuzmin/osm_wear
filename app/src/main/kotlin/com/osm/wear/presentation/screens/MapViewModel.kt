package com.osm.wear.presentation.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.data.gpx.GpxRepository
import com.osm.wear.data.location.LocationRepository
import com.osm.wear.data.map.MapDownloadManager
import com.osm.wear.data.map.MapRegionCatalog
import com.osm.wear.data.navigation.NavigationEngine
import com.osm.wear.data.recording.TrackRecorder
import com.osm.wear.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val locationRepo    = LocationRepository(context)
    val downloadManager = MapDownloadManager(context)
    val gpxRepo         = GpxRepository(context)
    private val recorder = TrackRecorder(context, context.filesDir)

    // ── GPS battery mode ───────────────────────────────────────────────────────
    private val _gpsBatteryMode = MutableStateFlow(GpsBatteryMode.BALANCED)
    val gpsBatteryMode: StateFlow<GpsBatteryMode> = _gpsBatteryMode.asStateFlow()

    // ── Location ───────────────────────────────────────────────────────────────
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(false)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    private var locationJob: Job? = null

    // ── Map state ──────────────────────────────────────────────────────────────
    private val _activeMapFile = MutableStateFlow<File?>(null)
    val activeMapFile: StateFlow<File?> = _activeMapFile.asStateFlow()

    private val _zoomLevel = MutableStateFlow(14)
    val zoomLevel: StateFlow<Int> = _zoomLevel.asStateFlow()

    private val _centerLat = MutableStateFlow(52.52)
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

    // ── Recording state ────────────────────────────────────────────────────────
    val recordingSession: StateFlow<RecordingSession?> = recorder.session

    // ── Navigation state ───────────────────────────────────────────────────────
    private val _navigationState = MutableStateFlow<NavigationState?>(null)
    val navigationState: StateFlow<NavigationState?> = _navigationState.asStateFlow()

    private var navigationJob: Job? = null

    init {
        refreshDownloadedRegions()
        loadFirstAvailableMap()
    }

    // ── GPS battery mode ───────────────────────────────────────────────────────

    fun setGpsBatteryMode(mode: GpsBatteryMode) {
        if (_gpsBatteryMode.value == mode) return
        _gpsBatteryMode.value = mode
        // Restart location tracking with new mode if currently active
        if (_isTrackingLocation.value) {
            stopLocationTracking()
            startLocationTracking()
        }
    }

    // ── Location ───────────────────────────────────────────────────────────────

    fun startLocationTracking() {
        if (_isTrackingLocation.value) return
        _isTrackingLocation.value = true
        locationJob = viewModelScope.launch {
            locationRepo.locationFlow(_gpsBatteryMode.value).collect { location ->
                _userLocation.value = location
                // Feed live location into navigation engine
                _navigationState.value?.let { nav ->
                    _navigationState.value = NavigationEngine.update(nav, location)
                }
            }
        }
    }

    fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
        _isTrackingLocation.value = false
    }

    fun centerOnUserLocation() {
        _userLocation.value?.let { loc ->
            _centerLat.value = loc.latitude
            _centerLon.value = loc.longitude
        }
    }

    // ── Map controls ───────────────────────────────────────────────────────────

    fun zoomIn()  { _zoomLevel.value = (_zoomLevel.value + 1).coerceAtMost(20) }
    fun zoomOut() { _zoomLevel.value = (_zoomLevel.value - 1).coerceAtLeast(3) }

    fun setCenter(lat: Double, lon: Double) {
        _centerLat.value = lat
        _centerLon.value = lon
    }

    fun setActiveMapFile(file: File?) { _activeMapFile.value = file }

    // ── Downloads ──────────────────────────────────────────────────────────────

    fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.downloadRegion(region).collect { progress ->
                if (progress.status == RegionStatus.DOWNLOADED) {
                    refreshDownloadedRegions()
                    if (_activeMapFile.value == null) loadFirstAvailableMap()
                }
            }
        }
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.deleteRegion(region)
            refreshDownloadedRegions()
            val activeFile = _activeMapFile.value
            if (activeFile != null && !activeFile.exists()) {
                _activeMapFile.value = null
                loadFirstAvailableMap()
            }
        }
    }

    fun selectRegionAsActiveMap(region: MapRegion) {
        _activeMapFile.value = downloadManager.getLocalMapFile(region)
    }

    private fun refreshDownloadedRegions() {
        _downloadedRegionIds.value = downloadManager.getDownloadedRegionIds().toSet()
    }

    private fun loadFirstAvailableMap() {
        val ids = downloadManager.getDownloadedRegionIds()
        if (ids.isNotEmpty()) {
            val region = MapRegionCatalog.findById(ids.first())
                ?: MapRegion(ids.first(), ids.first(), "", "", 0L)
            _activeMapFile.value = downloadManager.getLocalMapFile(region)
        }
    }

    // ── GPX ────────────────────────────────────────────────────────────────────

    fun importGpxFromUri(uri: Uri) {
        viewModelScope.launch { gpxRepo.importFromUri(uri) }
    }

    fun deleteGpxTrack(trackId: String) {
        viewModelScope.launch { gpxRepo.deleteTrack(trackId) }
    }

    fun toggleGpxTrackVisibility(trackId: String) {
        val track = gpxTracks.value.find { it.id == trackId } ?: return
        gpxRepo.setTrackVisibility(trackId, !track.isVisible)
    }

    // ── Recording ──────────────────────────────────────────────────────────────

    fun startRecording() {
        // Switch to HIGH_ACCURACY for recording
        setGpsBatteryMode(GpsBatteryMode.HIGH_ACCURACY)
        if (!_isTrackingLocation.value) startLocationTracking()
        recorder.start(locationRepo.locationFlow(GpsBatteryMode.HIGH_ACCURACY))
    }

    fun pauseRecording() {
        recorder.pause()
        // Revert to BALANCED while paused to save battery
        setGpsBatteryMode(GpsBatteryMode.BALANCED)
    }

    fun resumeRecording() {
        setGpsBatteryMode(GpsBatteryMode.HIGH_ACCURACY)
        recorder.resume(locationRepo.locationFlow(GpsBatteryMode.HIGH_ACCURACY))
    }

    fun stopRecording() {
        val savedFile = recorder.stop()
        setGpsBatteryMode(GpsBatteryMode.BALANCED)
        savedFile?.let { file ->
            viewModelScope.launch {
                gpxRepo.importFromFile(file)
            }
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        setGpsBatteryMode(GpsBatteryMode.BALANCED)
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    fun startNavigation(track: GpxTrack) {
        val waypoints = NavigationEngine.buildWaypoints(track)
        if (waypoints.isEmpty()) return

        _navigationState.value = NavigationState(
            track = track,
            waypoints = waypoints,
            nextWaypointIndex = 0,
            distanceToNextM = waypoints.first().distanceToNext,
            distanceRemainingM = waypoints.last().distanceFromStart,
            offTrackM = 0.0
        )

        // Switch to HIGH_ACCURACY for navigation
        setGpsBatteryMode(GpsBatteryMode.HIGH_ACCURACY)
        if (!_isTrackingLocation.value) startLocationTracking()
    }

    fun stopNavigation() {
        _navigationState.value = null
        setGpsBatteryMode(GpsBatteryMode.BALANCED)
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancel()
    }
}
