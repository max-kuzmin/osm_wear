package com.osm.wear.repositories

import com.osm.wear.models.GpxPoint
import com.osm.wear.models.enums.NavigationMode

interface IRouteRepository {
    suspend fun fetchRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: NavigationMode
    ): List<GpxPoint>
}

