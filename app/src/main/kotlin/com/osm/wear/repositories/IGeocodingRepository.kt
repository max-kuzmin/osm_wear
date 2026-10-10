package com.osm.wear.repositories

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.enums.NavigationMode

import org.mapsforge.core.model.BoundingBox

interface IGeocodingRepository {
    suspend fun reverseGeocode(lat: Double, lon: Double): GeocodeResult?
    suspend fun searchAddress(query: String, bbox: BoundingBox? = null): List<GeocodeResult>
    suspend fun fetchRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: NavigationMode
    ): List<GeoPoint>
    suspend fun fetchNavigationRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: NavigationMode
    ): NavigationRoute?
}

data class GeocodeResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double
)
