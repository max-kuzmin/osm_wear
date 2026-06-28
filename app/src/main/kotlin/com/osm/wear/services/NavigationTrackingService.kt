package com.osm.wear.services

import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.GpxFile
import com.osm.wear.models.UserLocation
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.NavigationState
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IRegionRepository
import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IGeocodingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.util.Log
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationTrackingService @Inject constructor(
    private val navigationService: INavigationService,
    private val preferencesRepository: IPreferencesRepository,
    private val regionRepository: IRegionRepository,
    private val checkGpxCoverageUseCase: CheckGpxCoverageUseCase,
    private val cursorRepository: ICursorRepository,
    private val routeRepo: IGeocodingRepository
) : INavigationTrackingService {

    private val _navigationState = MutableStateFlow<NavigationState?>(null)
    override val navigationState: StateFlow<NavigationState?> = _navigationState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationJob: Job? = null

    override fun navigateTo(route: String) {
        scope.launch {
            _navigationEvents.emit(route)
        }
    }

    override fun startNavigation(
        gpx: GpxFile,
        initialLocation: UserLocation?
    ): String? {
        val mapFile = regionRepository.getActiveMapFile()
        if (mapFile == null || !mapFile.exists()) {
            return "Download a map for this region first"
        }

        val navMode = preferencesRepository.getNavigationMode()
        val isCovered = checkGpxCoverageUseCase(gpx)

        if (!isCovered) {
            return "GPX track is outside the downloaded map area"
        }

        var nav = navigationService.buildInitialNavigationState(gpx, mapFile, navMode)
            ?: return if (navMode == NavigationMode.GPX_ONLY) {
                "Failed to build navigation state"
            } else {
                "Failed to map route to roads"
            }
        
        initialLocation?.let { loc ->
            nav = navigationService.updateNavigationState(nav, loc)
        }
        
        _navigationState.value = nav
        
        // Start automatic background location tracking for navigation updates
        startLocationTracking()

        navigationService.announce("Navigation started")
        navigationService.startForegroundService()
        return null // success
    }

    override fun stopNavigation() {
        stopLocationTracking()
        _navigationState.value = null
        
        navigationService.announce("Navigation stopped")
        navigationService.stopForegroundService()
    }

    override fun updateLocation(loc: UserLocation) {
        _navigationState.value?.let { nav ->
            val updated = navigationService.updateNavigationState(nav, loc)
            _navigationState.value = updated
        }
    }

    private fun startLocationTracking() {
        locationJob?.cancel()
        locationJob = scope.launch {
            // Collect coordinates at HIGH_ACCURACY during active navigation
            cursorRepository.locationFlow(GpsBatteryMode.HIGH_ACCURACY).collect { loc ->
                updateLocation(loc)
            }
        }
    }

    private fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
    }

    override fun buildRouteToPoint(
        target: GpxPoint,
        currentLoc: UserLocation?,
        onGpxCreated: (GpxFile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val center = preferencesRepository.getMapCenter()
        val startLat = currentLoc?.latitude ?: center.lat
        val startLon = currentLoc?.longitude ?: center.lon
        val mode = preferencesRepository.getNavigationMode()

        scope.launch {
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
                Log.e("NavigationRepository", "buildRouteToPoint failed", e)
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
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
                sin(dLon / 2).let { it * it }
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * atan2(sqrt(clampedH), sqrt(1.0 - clampedH))
    }
}
