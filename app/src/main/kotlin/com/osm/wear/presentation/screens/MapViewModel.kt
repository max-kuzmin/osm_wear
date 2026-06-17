package com.osm.wear.presentation.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.data.gpx.GpxRepository
import com.osm.wear.data.location.LocationRepository
import com.osm.wear.data.map.MapDownloadManager
import com.osm.wear.data.map.MapRegionCatalog
import com.osm.wear.data.navigation.NavigationEngine
import com.osm.wear.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── UI state ────────────────────────────────────────────────────────────────

data class MapUiState(
    /** The .map file currently rendered on the main screen, or null if none. */
    val activeMapFile: File? = null,
    /** ID of the active region (used to highlight in the regions list). */
    val activeRegionId: String? = null,
    /** The GPX file currently overlaid on the map, or null if none. */
    val activeGpxFile: GpxFile? = null,
    /** Current zoom level (3..20). */
    val zoomLevel: Int = 14,
    /** Map centre latitude. */
    val centerLat: Double = 0.0,
    /** Map centre longitude. */
    val centerLon: Double = 0.0,
    /** Whether the map should snap to the user's current location. */
    val followLocation: Boolean = true,
    /** Current navigation state, or null when not navigating. */
    val navigationState: NavigationState? = null,
    /** Battery mode for GPS. */
    val gpsBatteryMode: GpsBatteryMode = GpsBatteryMode.BALANCED
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepo: LocationRepository,
    private val downloadManager: MapDownloadManager,
    private val gpxRepo: GpxRepository,
    private val navEngine: NavigationEngine,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    // ── UI state ───────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ── Location ───────────────────────────────────────────────────────────────
    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    private var locationJob: Job? = null

    // ── Download ───────────────────────────────────────────────────────────────
    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    // ── GPX files ──────────────────────────────────────────────────────────────
    val gpxFiles: StateFlow<List<GpxFile>> =
        gpxRepo.files.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Downloaded regions ─────────────────────────────────────────────────────
    private val _downloadedRegions = MutableStateFlow<List<DownloadedRegion>>(emptyList())
    val downloadedRegions: StateFlow<List<DownloadedRegion>> = _downloadedRegions.asStateFlow()

    val groupedRegions: StateFlow<Map<String, List<MapRegion>>> = MutableStateFlow(
        MapRegionCatalog.all.groupBy { it.continent }
    ).asStateFlow()

    init {
        startLocationTracking()
        centerOnLocation()
        refreshDownloadedRegions()
        autoLoadFirstRegion()
    }

    // ── Location ───────────────────────────────────────────────────────────────

    private fun startLocationTracking() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepo.locationFlow(_uiState.value.gpsBatteryMode).collect { loc ->
                _currentLocation.value = loc
                if (_uiState.value.followLocation) {
                    _uiState.update { it.copy(centerLat = loc.latitude, centerLon = loc.longitude) }
                }
                // Feed navigation engine
                _uiState.value.navigationState?.let { nav ->
                    val updated = navEngine.update(nav, loc)
                    _uiState.update { it.copy(navigationState = updated) }
                }
            }
        }
    }

    fun centerOnLocation() {
        _uiState.update { it.copy(followLocation = true) }
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
    }

    fun onPermissionsGranted() {
        startLocationTracking()
    }

    fun setGpsBatteryMode(mode: GpsBatteryMode) {
        _uiState.update { it.copy(gpsBatteryMode = mode) }
        startLocationTracking()
    }

    // ── Map pan / zoom ─────────────────────────────────────────────────────────

    fun zoomIn()  { _uiState.update { it.copy(zoomLevel = (it.zoomLevel + 1).coerceAtMost(20)) } }
    fun zoomOut() { _uiState.update { it.copy(zoomLevel = (it.zoomLevel - 1).coerceAtLeast(3)) } }

    fun onMapPanned(newLat: Double, newLon: Double) {
        _uiState.update { it.copy(centerLat = newLat, centerLon = newLon, followLocation = false) }
    }

    // ── Regions ────────────────────────────────────────────────────────────────

    fun setActiveRegion(region: MapRegion) {
        val file = downloadManager.getLocalFile(region)
        if (!file.exists()) return
        _uiState.update {
            it.copy(
                activeMapFile = file,
                activeRegionId = region.id
            )
        }
        prefs.edit().putString("active_region_id", region.id).apply()
        refreshDownloadedRegions()
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.deleteRegion(region)
            if (_uiState.value.activeRegionId == region.id) {
                _uiState.update { it.copy(activeMapFile = null, activeRegionId = null) }
                prefs.edit().remove("active_region_id").apply()
            }
            refreshDownloadedRegions()
            autoLoadFirstRegion()
        }
    }

    /**
     * Downloads [region] in the foreground (suspends until done).
     * Callers observe [downloadState] for progress.
     */
    fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.downloadRegion(region)
            refreshDownloadedRegions()
            // Auto-activate the first downloaded region if none is active
            if (_uiState.value.activeMapFile == null) {
                setActiveRegion(region)
            }
        }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun refreshDownloadedRegions() {
        _downloadedRegions.value = downloadManager.getDownloadedRegions(
            catalog = MapRegionCatalog.all,
            activeId = _uiState.value.activeRegionId
        )
    }

    private fun autoLoadFirstRegion() {
        if (_uiState.value.activeMapFile != null) return
        val downloaded = downloadManager.getDownloadedRegions(MapRegionCatalog.all, null)
        if (downloaded.isEmpty()) return

        val lastSelectedId = prefs.getString("active_region_id", null)
        val target = downloaded.find { it.region.id == lastSelectedId } ?: downloaded.first()
        
        val file = File(target.filePath)
        if (file.exists()) {
            _uiState.update { it.copy(activeMapFile = file, activeRegionId = target.region.id) }
            if (lastSelectedId != target.region.id) {
                prefs.edit().putString("active_region_id", target.region.id).apply()
            }
            refreshDownloadedRegions()
        }
    }

    // ── GPX ────────────────────────────────────────────────────────────────────

    fun importGpxFile(uri: Uri) {
        viewModelScope.launch { gpxRepo.importFromUri(uri) }
    }

    fun deleteGpxFile(fileId: String) {
        viewModelScope.launch {
            if (_uiState.value.activeGpxFile?.id == fileId) {
                _uiState.update { it.copy(activeGpxFile = null) }
            }
            gpxRepo.deleteFile(fileId)
        }
    }

    /** Sets the active GPX file to render on the map and returns to map screen. */
    fun setActiveGpxFile(gpxFile: GpxFile) {
        gpxRepo.setActive(gpxFile.id)
        _uiState.update { it.copy(activeGpxFile = gpxFile) }
    }

    fun clearActiveGpxFile() {
        gpxRepo.clearActive()
        _uiState.update { it.copy(activeGpxFile = null) }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    fun startNavigation() {
        val gpx = _uiState.value.activeGpxFile ?: return
        val waypoints = navEngine.buildWaypoints(gpx)
        if (waypoints.isEmpty()) return
        val nav = NavigationState(
            isActive = true,
            gpxFile = gpx,
            waypoints = waypoints,
            currentWaypointIndex = 0,
            distanceToNextTurnM = waypoints.first().distanceToNextM,
            bearingToNextTurn = waypoints.first().bearingToNext,
            totalRemainingM = waypoints.sumOf { it.distanceToNextM.toDouble() }.toFloat(),
            isOffTrack = false,
            lastAlertedWaypointIndex = -1
        )
        _uiState.update { it.copy(navigationState = nav) }
        // Switch to high accuracy GPS for navigation
        setGpsBatteryMode(GpsBatteryMode.HIGH_ACCURACY)
    }

    fun stopNavigation() {
        _uiState.update { it.copy(navigationState = null) }
        setGpsBatteryMode(GpsBatteryMode.BALANCED)
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        navEngine.release()
    }
}
