package com.osm.wear.repositories

interface IGeocodingRepository {
    suspend fun reverseGeocode(lat: Double, lon: Double): GeocodeResult?
    suspend fun searchAddress(query: String): List<GeocodeResult>
}

data class GeocodeResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double
)
