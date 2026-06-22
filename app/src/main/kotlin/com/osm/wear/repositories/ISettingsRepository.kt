package com.osm.wear.repositories

import com.osm.wear.models.MapTheme
import com.osm.wear.models.NavigationAlertMode
import com.osm.wear.models.NavigationMode
import com.osm.wear.models.GpxPoint
import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    fun getMapCenterLat(): Double
    fun getMapCenterLon(): Double
    fun getMapZoomLevel(): Int
    fun getMapFollowLocation(): Boolean
    fun getMapTheme(): MapTheme
    fun getNavigationAlertMode(): NavigationAlertMode
    fun getNavigationMode(): NavigationMode
    fun getTappedPoint(): GpxPoint?
    fun getActiveRegionId(): String?
    fun getActiveGpxId(): String?

    fun setMapCenterLat(lat: Double)
    fun setMapCenterLon(lon: Double)
    fun setMapZoomLevel(zoom: Int)
    fun setMapFollowLocation(follow: Boolean)
    fun setMapTheme(theme: MapTheme)
    fun setNavigationAlertMode(mode: NavigationAlertMode)
    fun setNavigationMode(mode: NavigationMode)
    fun setTappedPoint(point: GpxPoint?)
    fun setActiveRegionId(id: String?)
    fun setActiveGpxId(id: String?)
}

