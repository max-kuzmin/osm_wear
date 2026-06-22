package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpsBatteryMode
import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.NavigationMode
import com.osm.wear.models.NavigationState
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IRouteRepository
import com.osm.wear.services.INavigationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val navigationService: INavigationService,
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

    fun startNavigation(gpx: GpxFile, initialLocation: UserLocation?, onBatteryModeChange: (GpsBatteryMode) -> Unit) {
        var nav = navigationService.buildInitialNavigationState(gpx) ?: return
        
        initialLocation?.let { loc ->
            nav = navigationService.updateNavigationState(nav, loc)
        }
        
        _navigationState.value = nav
        onBatteryModeChange(GpsBatteryMode.HIGH_ACCURACY)
        
        navigationService.announce("Navigation started")
        navigationService.startForegroundService()
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
        mapCenterLat: Double, 
        mapCenterLon: Double, 
        mode: NavigationMode,
        onGpxCreated: (GpxFile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val startLat = currentLoc?.latitude ?: mapCenterLat
        val startLon = currentLoc?.longitude ?: mapCenterLon

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
