package com.osm.wear.data_sources

import com.osm.wear.models.UserLocation
import com.osm.wear.models.enums.GpsBatteryMode
import kotlinx.coroutines.flow.Flow

interface IDeviceLocationDataSource {
    fun hasLocationPermission(): Boolean
    fun locationFlow(mode: GpsBatteryMode): Flow<UserLocation>
    suspend fun getLastKnownLocation(): UserLocation?
}
