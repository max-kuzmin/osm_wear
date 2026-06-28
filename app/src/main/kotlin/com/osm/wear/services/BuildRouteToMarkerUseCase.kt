package com.osm.wear.services

import android.util.Log
import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.*

class BuildRouteToMarkerUseCase @Inject constructor(
    private val routeRepo: IGeocodingRepository,
    private val preferencesRepository: IPreferencesRepository
) {
    suspend operator fun invoke(
        target: GpxPoint,
        currentLoc: UserLocation?
    ): Result<GpxFile> = withContext(Dispatchers.Default) {
        try {
            val center = preferencesRepository.getMapCenter()
            val startLat = currentLoc?.latitude ?: center.lat
            val startLon = currentLoc?.longitude ?: center.lon
            val mode = preferencesRepository.getNavigationMode()

            val routePoints = routeRepo.fetchRoute(startLat, startLon, target.lat, target.lon, mode)
            if (routePoints.isEmpty()) {
                return@withContext Result.failure(Exception("Routing failed. Check your internet connection."))
            }

            val gpx = GpxFile(
                id = "path_finder",
                name = "Path Finder",
                filePath = "",
                trackPoints = routePoints,
                totalDistanceKm = calculateDistanceKm(routePoints),
                isActive = true
            )
            Result.success(gpx)
        } catch (e: Exception) {
            Log.e("BuildRouteToMarkerUseCase", "Failed to build route to point", e)
            Result.failure(e)
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
