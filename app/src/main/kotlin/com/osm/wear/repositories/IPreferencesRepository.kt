package com.osm.wear.repositories

import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.GpxPoint

interface IPreferencesRepository {
    fun getMapCenter(): GpxPoint
    fun getMapZoomLevel(): Int
    fun getMapFollowLocation(): Boolean
    fun getMapTheme(): MapTheme
    fun getNavigationMode(): NavigationMode
    fun getGpsBatteryMode(): GpsBatteryMode

    fun setMapCenter(lat: Double, lon: Double)
    fun setMapZoomLevel(zoom: Int)
    fun setMapFollowLocation(follow: Boolean)
    fun setMapTheme(theme: MapTheme)
    fun setNavigationMode(mode: NavigationMode)
    fun setGpsBatteryMode(mode: GpsBatteryMode)
}
