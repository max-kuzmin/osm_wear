package com.osm.wear.repositories

import com.osm.wear.models.GpsBatteryMode
import com.osm.wear.models.UserLocation
import kotlinx.coroutines.flow.Flow

interface ILocationRepository {
    fun locationFlow(mode: GpsBatteryMode): Flow<UserLocation>
    suspend fun getLastKnownLocation(): UserLocation?
}

