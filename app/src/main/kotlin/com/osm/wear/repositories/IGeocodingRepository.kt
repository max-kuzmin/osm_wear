package com.osm.wear.repositories

import com.osm.wear.models.GpxPoint
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
    ): List<GpxPoint>
}

data class GeocodeResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double
)
