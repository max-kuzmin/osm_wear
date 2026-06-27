package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.GpxFile
import com.osm.wear.models.UserLocation
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.NavigationState
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.IRouteRepository
import com.osm.wear.repositories.IMapFileRepository
import com.osm.wear.repositories.ISettingsRepository
import com.osm.wear.services.INavigationService
import org.mapsforge.map.reader.MapFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val navigationService: INavigationService,
    private val settingsRepository: ISettingsRepository,
    private val mapFileRepository: IMapFileRepository,
    private val routeRepo: IRouteRepository
) : ViewModel() {

    private val _navigationState = MutableStateFlow<NavigationState?>(null)
    val navigationState: StateFlow<NavigationState?> = _navigationState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }

    /**
     * Checks if the given GPX file's track is covered by the currently active map file.
     * Returns true if a map is loaded and covers the track area.
     */
    fun isGpxCoveredByMap(gpx: GpxFile): Boolean {
        val file = mapFileRepository.getActiveMapFile() ?: return false
        if (!file.exists()) return false

        return try {
            val mapFile = MapFile(file)
            try {
                val mapBBox = mapFile.boundingBox()
                if (mapBBox == null) return false

                val trackPoints = gpx.trackPoints
                if (trackPoints.isEmpty()) return false

                var sumLat = 0.0
                var sumLon = 0.0
                for (pt in trackPoints) {
                    sumLat += pt.lat
                    sumLon += pt.lon
                }
                val centroidLat = sumLat / trackPoints.size
                val centroidLon = sumLon / trackPoints.size

                val covered = centroidLat >= mapBBox.minLatitude &&
                        centroidLat <= mapBBox.maxLatitude &&
                        centroidLon >= mapBBox.minLongitude &&
                        centroidLon <= mapBBox.maxLongitude

                if (!covered) {
                    android.util.Log.d("NavigationViewModel", "GPX centroid ($centroidLat, $centroidLon) is outside map bbox " +
                            "(${mapBBox.minLatitude}-${mapBBox.maxLatitude}, ${mapBBox.minLongitude}-${mapBBox.maxLongitude})")
                }

                covered
            } finally {
                mapFile.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("NavigationViewModel", "Failed to check GPX coverage", e)
            false
        }
    }

    /** Returns true if any map file is currently active. */
    fun hasActiveMapFile(): Boolean {
        return mapFileRepository.getActiveMapFile() != null
    }

    /**
     * Starts navigation on the given GPX file.
     * Returns an error message if navigation cannot start, or null on success.
     */
    fun startNavigation(
        gpx: GpxFile,
        initialLocation: UserLocation?,
        onBatteryModeChange: (GpsBatteryMode) -> Unit
    ): String? {
        val mapFile = mapFileRepository.getActiveMapFile()
        if (mapFile == null || !mapFile.exists()) {
            return "Download a map for this region first"
        }

        val navMode = settingsRepository.getNavigationMode()
        val isCovered = isGpxCoveredByMap(gpx)

        if (!isCovered) {
            return "GPX track is outside the downloaded map area"
        }

        val finalMapFile = mapFile
        var nav = navigationService.buildInitialNavigationState(gpx, finalMapFile, navMode)
            ?: return if (navMode == NavigationMode.GPX_ONLY) {
                "Failed to build navigation state"
            } else {
                "Failed to map route to roads"
            }
        
        initialLocation?.let { loc ->
            nav = navigationService.updateNavigationState(nav, loc)
        }
        
        _navigationState.value = nav
        onBatteryModeChange(GpsBatteryMode.HIGH_ACCURACY)
        
        navigationService.announce("Navigation started")
        navigationService.startForegroundService()
        return null // success
    }

    fun stopNavigation(onBatteryModeChange: (GpsBatteryMode) -> Unit) {
        _navigationState.value = null
        onBatteryModeChange(GpsBatteryMode.BALANCED)
        
        navigationService.announce("Navigation stopped")
        navigationService.stopForegroundService()
    }

    fun updateLocation(loc: UserLocation) {
        _navigationState.value?.let { nav ->
            val updated = navigationService.updateNavigationState(nav, loc)
            _navigationState.value = updated
        }
    }

    fun startNavigationToPoint(
        target: GpxPoint,
        currentLoc: UserLocation?,
        mode: NavigationMode,
        onGpxCreated: (GpxFile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val startLat = currentLoc?.latitude ?: settingsRepository.getMapCenterLat()
        val startLon = currentLoc?.longitude ?: settingsRepository.getMapCenterLon()

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

                onGpxCreated(gpx)
            } catch (e: Exception) {
                android.util.Log.e("NavigationViewModel", "startNavigationToPoint failed", e)
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
        navigationService.release()
    }
}
