package com.osm.wear.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.osm.wear.domain.model.UserLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

/**
 * Provides GPS location updates using Android's FusedLocationProviderClient.
 *
 * On Wear OS / Galaxy Watch 7, GPS is built-in and works standalone.
 */
class LocationRepository(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Emits GPS location updates as a cold [Flow].
     * Requires [Manifest.permission.ACCESS_FINE_LOCATION] to be granted.
     */
    fun locationUpdates(
        intervalMs: Long = 3_000L,
        minUpdateDistanceMeters: Float = 2f
    ): Flow<UserLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val userLocation = loc.toUserLocation()
                    Log.d(TAG, "Location update: ${userLocation.latitude}, ${userLocation.longitude}")
                    trySend(userLocation)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Log.d(TAG, "Started location updates")

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
            Log.d(TAG, "Stopped location updates")
        }
    }.catch { e ->
        Log.e(TAG, "Location error", e)
    }

    /** Returns the last known location immediately (may be null or stale). */
    suspend fun getLastKnownLocation(): UserLocation? {
        if (!hasLocationPermission()) return null
        return try {
            val task = fusedClient.lastLocation
            // Use a simple blocking approach since this is called from a coroutine
            var result: Location? = null
            task.addOnSuccessListener { result = it }
            // Give it a moment to resolve
            kotlinx.coroutines.delay(500)
            result?.toUserLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last location", e)
            null
        }
    }

    private fun Location.toUserLocation() = UserLocation(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        bearing = if (hasBearing()) bearing else null,
        timestamp = time
    )

    companion object {
        private const val TAG = "LocationRepository"
    }
}
