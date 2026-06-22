package com.osm.wear.view_models

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.repositories.ILocationRepository
import com.osm.wear.repositories.IRouteRepository
import com.osm.wear.repositories.ISettingsRepository
import com.osm.wear.services.INavigationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MapViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val locationRepo: ILocationRepository,
    private val downloadManager: com.osm.wear.repositories.MapDownloadRepository,
    private val gpxRepo: IGpxRepository,
    private val routeRepo: IRouteRepository,
    private val settingsRepository: ISettingsRepository,
    private val navigationService: INavigationService,
    private val mapRegionCatalogService: com.osm.wear.services.IMapRegionCatalogService
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
        mapRegionCatalogService.all.groupBy { it.continent }
    ).asStateFlow()

    init {
        startLocationTracking()
        loadMapState()
        refreshDownloadedRegions()
        autoLoadFirstRegion()
        autoLoadActiveGpx()
        scanGpxFolders()
    }

    private fun loadMapState() {
        val lat = settingsRepository.getMapCenterLat()
        val lon = settingsRepository.getMapCenterLon()
        val zoom = settingsRepository.getMapZoomLevel()
        val follow = settingsRepository.getMapFollowLocation()
        val theme = settingsRepository.getMapTheme()
        val alertMode = settingsRepository.getNavigationAlertMode()
        val tappedPoint = settingsRepository.getTappedPoint()
        val mode = settingsRepository.getNavigationMode()

        _uiState.update { 
            it.copy(
                centerLat = lat, 
                centerLon = lon, 
                zoomLevel = zoom, 
                followLocation = follow,
                mapTheme = theme,
                navigationAlertMode = alertMode,
                tappedPoint = tappedPoint,
                navigationMode = mode
            ) 
        }
        navigationService.setAlertMode(alertMode)

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
        settingsRepository.setMapTheme(state.mapTheme)
        settingsRepository.setNavigationAlertMode(state.navigationAlertMode)
        settingsRepository.setNavigationMode(state.navigationMode)
        settingsRepository.setTappedPoint(state.tappedPoint)
    }

    // ── Location ───────────────────────────────────────────────────────────────

    private fun startLocationTracking() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepo.locationFlow(_uiState.value.gpsBatteryMode).collect { loc ->
                _currentLocation.value = loc
                if (_uiState.value.followLocation) {
                    _uiState.update { it.copy(centerLat = loc.latitude, centerLon = loc.longitude) }
                    persistMapState()
                }
                // Feed navigation engine
                _uiState.value.navigationState?.let { nav ->
                    val updated = navigationService.updateNavigationState(nav, loc)
                    _uiState.update { it.copy(navigationState = updated) }
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

    fun onPermissionsGranted() {
        startLocationTracking()
    }

    fun setGpsBatteryMode(mode: GpsBatteryMode) {
        _uiState.update { it.copy(gpsBatteryMode = mode) }
        startLocationTracking()
    }

    // ── Map pan / zoom ─────────────────────────────────────────────────────────

    fun zoomIn() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel + 1).coerceAtMost(20)) }
        persistMapState()
    }

    fun zoomOut() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel - 1).coerceAtLeast(3)) }
        persistMapState()
    }

    fun setMapTheme(theme: MapTheme) {
        _uiState.update { it.copy(mapTheme = theme) }
        persistMapState()
    }

    fun setNavigationAlertMode(mode: NavigationAlertMode) {
        _uiState.update { it.copy(navigationAlertMode = mode) }
        navigationService.setAlertMode(mode)
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
        settingsRepository.setActiveRegionId(region.id)
        refreshDownloadedRegions()
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.deleteRegion(region)
            if (_uiState.value.activeRegionId == region.id) {
                _uiState.update { it.copy(activeMapFile = null, activeRegionId = null) }
                settingsRepository.setActiveRegionId(null)
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
            offlineMapService.downloadRegion(region)
            refreshDownloadedRegions()
            // Auto-activate the first downloaded region if none is active
            if (_uiState.value.activeMapFile == null) {
                setActiveRegion(region)
            }
        }
    }

    fun cancelDownload() {
        offlineMapService.cancelDownload()
    }

    fun refreshDownloadedRegions() {
        _downloadedRegions.value = downloadManager.getDownloadedRegions(
            catalog = mapRegionCatalogService.all,
            activeId = _uiState.value.activeRegionId
        )
    }

    private fun autoLoadFirstRegion() {
        if (_uiState.value.activeMapFile != null) return
        val downloaded = downloadManager.getDownloadedRegions(mapRegionCatalogService.all, null)
        if (downloaded.isEmpty()) return

        val lastSelectedId = settingsRepository.getActiveRegionId()
        val target = downloaded.find { it.region.id == lastSelectedId } ?: downloaded.first()
        
        val file = File(target.filePath)
        if (file.exists()) {
            _uiState.update { it.copy(activeMapFile = file, activeRegionId = target.region.id) }
            if (lastSelectedId != target.region.id) {
                settingsRepository.setActiveRegionId(target.region.id)
            }
            refreshDownloadedRegions()
        }
    }

    // ── GPX ────────────────────────────────────────────────────────────────────



    private val _navigationEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }

    fun importGpxFile(uri: Uri, autoActivate: Boolean = false) {
        viewModelScope.launch {
            gpxRepo.importFromUri(uri).onSuccess { gpx ->
                if (autoActivate) {
                    setActiveGpxFile(gpx)
                    gpx.trackPoints.firstOrNull()?.let { startPt ->
                        _uiState.update { 
                            it.copy(
                                centerLat = startPt.lat,
                                centerLon = startPt.lon,
                                zoomLevel = 15,
                                followLocation = false
                            ) 
                        }
                        persistMapState()
                    }
                }
            }
        }
    }

    fun importGpxFromFile(file: File) {
        viewModelScope.launch { gpxRepo.importFromFile(file) }
    }

    fun scanGpxFolders() {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val foundFiles = mutableListOf<File>()
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloads.exists()) {
                    scanDirectory(downloads, foundFiles)
                }
                foundFiles.forEach { file ->
                    gpxRepo.importFromFile(file)
                }
                val foundNames = foundFiles.map { it.name }.toSet()
                val internalFiles = File(context.filesDir, "gpx").listFiles()?.filter { it.extension == "gpx" } ?: emptyList()
                internalFiles.forEach { internalFile ->
                    if (!foundNames.contains(internalFile.name)) {
                        gpxRepo.deleteFile(internalFile.name)
                    }
                }
            }
        }
    }

    private fun scanDirectory(dir: File, result: MutableList<File>) {
        if (!dir.exists()) return
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        }
        if (files == null) return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, result)
            } else if (file.extension.lowercase() == "gpx") {
                result.add(file)
            }
        }
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

    private fun autoLoadActiveGpx() {
        viewModelScope.launch {
            // Wait for gpxFiles to be populated from the repository
            gpxFiles.collect { files ->
                val active = files.find { it.isActive }
                _uiState.update { it.copy(activeGpxFile = active) }
            }
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    fun startNavigation() {
        val gpx = _uiState.value.activeGpxFile ?: return
        var nav = navigationService.buildInitialNavigationState(gpx) ?: return
        
        // Update navigation state immediately if location is available
        _currentLocation.value?.let { loc ->
            nav = navigationService.updateNavigationState(nav, loc)
        }
        _uiState.update { it.copy(navigationState = nav) }
        // Switch to high accuracy GPS for navigation
        setGpsBatteryMode(GpsBatteryMode.HIGH_ACCURACY)
        // Auto-enable locate me mode
        centerOnLocation()
        
        navigationService.announce("Navigation started")
        navigationService.startForegroundService()
    }

    fun stopNavigation() {
        _uiState.update { 
            val clearGpx = it.activeGpxFile?.id == "path_finder"
            it.copy(
                navigationState = null,
                activeGpxFile = if (clearGpx) null else it.activeGpxFile
            )
        }
        setGpsBatteryMode(GpsBatteryMode.BALANCED)
        
        navigationService.announce("Navigation stopped")
        navigationService.stopForegroundService()
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

    fun cycleNavigationMode() {
        val nextMode = when (_uiState.value.navigationMode) {
            NavigationMode.WALKING -> NavigationMode.CYCLING
            NavigationMode.CYCLING -> NavigationMode.DRIVING
            NavigationMode.DRIVING -> NavigationMode.WALKING
        }
        _uiState.update { it.copy(navigationMode = nextMode) }
        persistMapState()
    }

    fun startNavigationToPoint(onFailure: (String) -> Unit) {
        val target = _uiState.value.tappedPoint
        if (target == null) {
            onFailure("No target point selected.")
            return
        }
        
        val startLat: Double
        val startLon: Double
        val currentLoc = _currentLocation.value
        if (currentLoc != null) {
            startLat = currentLoc.latitude
            startLon = currentLoc.longitude
        } else {
            startLat = _uiState.value.centerLat
            startLon = _uiState.value.centerLon
        }

        val mode = _uiState.value.navigationMode

        viewModelScope.launch {
            try {
                val routePoints = routeRepo.fetchRoute(startLat, startLon, target.lat, target.lon, mode)
                if (routePoints.isEmpty()) {
                    onFailure("Routing failed. Check your internet connection.")
                    return@launch
                }
                
                val gpx = GpxFile(
                    id = "path_finder",
                    name = "Path Finder",
                    filePath = "",
                    trackPoints = routePoints,
                    totalDistanceKm = calculateDistanceKm(routePoints),
                    isActive = true
                )

                _uiState.update { it.copy(activeGpxFile = gpx) }
                startNavigation()
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "startNavigationToPoint failed", e)
                onFailure("Routing failed. Check your internet connection.")
            }
        }
    }

    private fun calculateDistanceKm(points: List<GpxPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineM(points[i - 1], points[i])
        }
        return total / 1000.0
    }

    private fun haversineM(a: GpxPoint, b: GpxPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(a.lat)) * kotlin.math.cos(Math.toRadians(b.lat)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(clampedH), kotlin.math.sqrt(1.0 - clampedH))
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        navigationService.release()
    }
}

