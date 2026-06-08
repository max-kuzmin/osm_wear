package com.osm.wear.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.osm.wear.domain.model.GpsBatteryMode
import com.osm.wear.domain.model.UserLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

/**
 * Provides GPS location updates using FusedLocationProviderClient.
 *
 * Battery strategy (controlled by [GpsBatteryMode]):
 *  POWER_SAVE    – 10 s / 20 m  – idle map browsing, minimal drain
 *  BALANCED      – 5 s  / 5 m   – normal use (default)
 *  HIGH_ACCURACY – 1 s  / 0 m   – active recording or navigation
 *
 * On Wear OS / Galaxy Watch 7 GPS is built-in and works standalone.
 */
class LocationRepository(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Emits GPS location updates as a cold [Flow].
     * The [mode] parameter controls accuracy vs. battery trade-off.
     */
    fun locationFlow(mode: GpsBatteryMode = GpsBatteryMode.BALANCED): Flow<UserLocation> =
        callbackFlow {
            if (!hasLocationPermission()) {
                close(SecurityException("Location permission not granted"))
                return@callbackFlow
            }

            val priority = when (mode) {
                GpsBatteryMode.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
                GpsBatteryMode.BALANCED      -> Priority.PRIORITY_HIGH_ACCURACY
                GpsBatteryMode.LOW_POWER     -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            val request = LocationRequest.Builder(priority, mode.intervalMs)
                .setMinUpdateIntervalMillis(mode.intervalMs / 2)
                .setMinUpdateDistanceMeters(mode.minDisplacementM)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        trySend(loc.toUserLocation())
                        Log.d(TAG, "[${mode.label}] ${loc.latitude}, ${loc.longitude} acc=${loc.accuracy}m")
                    }
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            Log.d(TAG, "Started location updates – mode=${mode.label}")

            awaitClose {
                fusedClient.removeLocationUpdates(callback)
                Log.d(TAG, "Stopped location updates – mode=${mode.label}")
            }
        }.catch { e -> Log.e(TAG, "Location error", e) }

    /** One-shot last known location (no active GPS, battery-free). */
    suspend fun getLastKnownLocation(): UserLocation? {
        if (!hasLocationPermission()) return null
        return try {
            val task = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            while (!task.isComplete) {
                kotlinx.coroutines.delay(100)
            }
            task.result?.toUserLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current location", e)
            null
        }
    }

    private fun Location.toUserLocation() = UserLocation(
        latitude  = latitude,
        longitude = longitude,
        accuracy  = accuracy,
        bearing   = if (hasBearing()) bearing else 0f,
        speed     = if (hasSpeed()) speed else 0f,
        timestamp = time
    )

    companion object {
        private const val TAG = "LocationRepository"
    }
}
