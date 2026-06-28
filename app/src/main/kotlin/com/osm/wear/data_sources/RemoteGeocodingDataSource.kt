package com.osm.wear.data_sources

import android.util.Log
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.repositories.GeocodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

interface IRemoteGeocodingDataSource {
    suspend fun reverseGeocode(lat: Double, lon: Double): GeocodeResult?
    suspend fun searchAddress(query: String): List<GeocodeResult>
    suspend fun fetchRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: NavigationMode
    ): List<GpxPoint>
}

class RemoteGeocodingDataSource(
    private val client: OkHttpClient
) : IRemoteGeocodingDataSource {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun reverseGeocode(lat: Double, lon: Double): GeocodeResult? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=en"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val displayName = json.optString("display_name", "")
                        val nameVal = json.optString("name", "")
                        
                        val parts = displayName.split(",")
                        val closestObject = nameVal.trim().takeIf { it.isNotEmpty() }
                            ?: parts.firstOrNull()?.trim()
                            ?: "Point"
                        
                        val address = if (parts.size > 2) {
                            parts.drop(1).dropLast(1).joinToString(",").trim()
                        } else if (parts.size > 1) {
                            parts.drop(1).joinToString(",").trim()
                        } else {
                            displayName
                        }
                        
                        return@withContext GeocodeResult(
                            name = closestObject,
                            address = address,
                            lat = lat,
                            lon = lon
                        )
                    }
                } else {
                    Log.e("RemoteGeocodingDataSource", "Reverse geocode response unsuccessful: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("RemoteGeocodingDataSource", "Failed to reverse geocode: ${e.message}", e)
        }
        return@withContext null
    }

    override suspend fun searchAddress(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&accept-language=en&limit=10"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val arr = JSONArray(body)
                        val results = mutableListOf<GeocodeResult>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val displayName = obj.optString("display_name", "")
                            val nameVal = obj.optString("name", "")
                            val lat = obj.getDouble("lat")
                            val lon = obj.getDouble("lon")
                            
                            val parts = displayName.split(",")
                            val closestObject = nameVal.trim().takeIf { it.isNotEmpty() }
                                ?: parts.firstOrNull()?.trim()
                                ?: "Point"
                            
                            val address = if (parts.size > 2) {
                                parts.drop(1).dropLast(1).joinToString(",").trim()
                            } else if (parts.size > 1) {
                                parts.drop(1).joinToString(",").trim()
                            } else {
                                displayName
                            }
                            
                            results.add(
                                GeocodeResult(
                                    name = closestObject,
                                    address = address,
                                    lat = lat,
                                    lon = lon
                                )
                            )
                        }
                        return@withContext results
                    }
                } else {
                    Log.e("RemoteGeocodingDataSource", "Search geocode response unsuccessful: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("RemoteGeocodingDataSource", "Failed to search address: ${e.message}", e)
        }
        return@withContext emptyList()
    }

    override suspend fun fetchRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: NavigationMode
    ): List<GpxPoint> = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/route/v1/${mode.profile}/$startLon,$startLat;$endLon,$endLat?overview=full&geometries=geojson"
        
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val routes = json.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")
                            val pts = mutableListOf<GpxPoint>()
                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                val lon = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                pts.add(GpxPoint(lat = lat, lon = lon))
                            }
                            return@withContext pts
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RemoteGeocodingDataSource", "Failed to fetch OSRM route: ${e.message}", e)
        }
        
        return@withContext emptyList()
    }
}
