package com.osm.wear.models

import kotlin.math.*

/**
 * Universal geographic point representing coordinates in WGS84 format.
 * Primary coordinate data model for native GeoJSON route architecture.
 *
 * @param lat Latitude in decimal degrees [-90.0, 90.0]
 * @param lon Longitude in decimal degrees [-180.0, 180.0]
 * @param ele Elevation in meters above sea level (default 0.0)
 * @param time Timestamp in milliseconds since Unix epoch (default 0L)
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double = 0.0,
    val time: Long = 0L
) {
    /**
     * Calculates great-circle distance to another [GeoPoint] in meters using the Haversine formula.
     */
    fun distanceTo(other: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2).pow(2)
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * atan2(sqrt(clampedH), sqrt(1.0 - clampedH))
    }

    /**
     * Calculates the initial geographic bearing (forward azimuth) from this point to [other] in degrees [0, 360).
     */
    fun bearingTo(other: GeoPoint): Float {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(other.lat)
        val dLon = Math.toRadians(other.lon - lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val initialBearing = Math.toDegrees(atan2(y, x))
        return ((initialBearing + 360) % 360).toFloat()
    }
}
