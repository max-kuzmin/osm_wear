package com.osm.wear.repositories

import android.util.Log
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.enums.NavigationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RouteRepositoryImpl(
    private val client: OkHttpClient
) : IRouteRepository {

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
            Log.e("RouteRepository", "Failed to fetch OSRM route: ${e.message}", e)
        }
        
        return@withContext emptyList()
    }
}

