package com.osm.wear.repositories

import com.osm.wear.data_sources.IDeviceLocationDataSource
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.UserLocation
import kotlinx.coroutines.flow.Flow

class CursorRepository(
    private val locationDataSource: IDeviceLocationDataSource
) : ICursorRepository {

    override fun locationFlow(mode: GpsBatteryMode): Flow<UserLocation> {
        return locationDataSource.locationFlow(mode)
    }

    override suspend fun getLastKnownLocation(): UserLocation? {
        return locationDataSource.getLastKnownLocation()
    }

    override fun isGpsEnabled(): Boolean {
        return locationDataSource.isGpsEnabled()
    }
}
