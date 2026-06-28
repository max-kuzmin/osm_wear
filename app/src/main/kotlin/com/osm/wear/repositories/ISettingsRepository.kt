package com.osm.wear.repositories

import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.GpxPoint

interface ISettingsRepository {
    fun getMapCenterLat(): Double
    fun getMapCenterLon(): Double
    fun getMapZoomLevel(): Int
    fun getMapFollowLocation(): Boolean
    fun getMapTheme(): MapTheme
    fun getNavigationAlertMode(): NavigationAlertMode
    fun getNavigationMode(): NavigationMode
    fun getGpsBatteryMode(): GpsBatteryMode
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
    fun setGpsBatteryMode(mode: GpsBatteryMode)
    fun setTappedPoint(point: GpxPoint?)
    fun setActiveRegionId(id: String?)
    fun setActiveGpxId(id: String?)
}

