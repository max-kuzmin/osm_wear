package com.osm.wear.services

import android.util.Log
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BuildRouteToMarkerUseCase @Inject constructor(
    private val geocodingRepository: IGeocodingRepository,
    private val preferencesRepository: IPreferencesRepository
) {
    /**
     * Builds and returns a [NavigationRoute] directly with ground-truth maneuvers from OSRM.
     */
    suspend operator fun invoke(
        target: GeoPoint,
        currentLoc: UserLocation?,
        targetName: String = "Path Finder"
    ): Result<NavigationRoute> = buildNavigationRoute(target, currentLoc, targetName)

    /**
     * Builds and returns a [NavigationRoute] directly with ground-truth maneuvers from OSRM.
     */
    suspend fun buildNavigationRoute(
        target: GeoPoint,
        currentLoc: UserLocation?,
        targetName: String = "Path Finder"
    ): Result<NavigationRoute> = withContext(Dispatchers.Default) {
        try {
            val (startLat, startLon) = resolveStartCoordinates(currentLoc)
            val mode = preferencesRepository.getNavigationMode()

            val route = geocodingRepository.fetchNavigationRoute(startLat, startLon, target.lat, target.lon, mode)
            if (route == null || route.steps.isEmpty()) {
                return@withContext Result.failure(Exception("Routing failed. Check your internet connection."))
            }
            Result.success(route)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build route to point", e)
            Result.failure(e)
        }
    }

    /**
     * Constructs a [RouteFile] domain model directly from the computed route.
     */
    suspend fun buildRouteFile(
        target: GeoPoint,
        currentLoc: UserLocation?,
        targetName: String = "Path Finder"
    ): Result<RouteFile> = withContext(Dispatchers.Default) {
        buildNavigationRoute(target, currentLoc, targetName).map { route ->
            RouteFile(
                id = "route_${System.currentTimeMillis()}",
                name = targetName,
                filePath = "",
                points = route.fullPolyline,
                totalDistanceKm = (route.totalDistanceM / 1000f).toDouble(),
                isActive = true,
                route = route
            )
        }
    }

    private fun resolveStartCoordinates(currentLoc: UserLocation?): Pair<Double, Double> {
        return if (currentLoc != null) {
            currentLoc.latitude to currentLoc.longitude
        } else {
            val center = preferencesRepository.getMapCenter()
            center.lat to center.lon
        }
    }

    companion object {
        private const val TAG = "BuildRouteToMarkerUseCase"
    }
}
