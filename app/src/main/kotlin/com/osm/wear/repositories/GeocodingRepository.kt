package com.osm.wear.repositories

import com.osm.wear.models.GpxPoint
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.data_sources.IRemoteGeocodingDataSource

import org.mapsforge.core.model.BoundingBox

class GeocodingRepository(
    private val remoteDataSource: IRemoteGeocodingDataSource
) : IGeocodingRepository {

    override suspend fun reverseGeocode(lat: Double, lon: Double): GeocodeResult? {
        return remoteDataSource.reverseGeocode(lat, lon)
    }

    override suspend fun searchAddress(query: String, bbox: BoundingBox?): List<GeocodeResult> {
        return remoteDataSource.searchAddress(query, bbox)
    }

    override suspend fun fetchRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: NavigationMode
    ): List<GpxPoint> {
        return remoteDataSource.fetchRoute(startLat, startLon, endLat, endLon, mode)
    }
}
