package com.osm.wear.repositories

import android.content.SharedPreferences
import com.osm.wear.models.MapTheme
import com.osm.wear.models.NavigationAlertMode
import com.osm.wear.models.NavigationMode
import com.osm.wear.models.GpxPoint

class SettingsRepositoryImpl(
    private val prefs: SharedPreferences
) : ISettingsRepository {

    override fun getMapCenterLat(): Double = prefs.getFloat("map_center_lat", 0.0f).toDouble()
    override fun getMapCenterLon(): Double = prefs.getFloat("map_center_lon", 0.0f).toDouble()
    override fun getMapZoomLevel(): Int = prefs.getInt("map_zoom_level", 14)
    override fun getMapFollowLocation(): Boolean = prefs.getBoolean("map_follow_location", true)
    
    override fun getMapTheme(): MapTheme {
        val themeStr = prefs.getString("map_theme", MapTheme.OSMARENDER.name) ?: MapTheme.OSMARENDER.name
        return try { MapTheme.valueOf(themeStr) } catch (e: Exception) { MapTheme.OSMARENDER }
    }
    
    override fun getNavigationAlertMode(): NavigationAlertMode {
        val alertModeStr = prefs.getString("nav_alert_mode", NavigationAlertMode.VOICE.name) ?: NavigationAlertMode.VOICE.name
        return try { NavigationAlertMode.valueOf(alertModeStr) } catch (e: Exception) { NavigationAlertMode.VOICE }
    }
    
    override fun getNavigationMode(): NavigationMode {
        val modeStr = prefs.getString("navigation_mode", NavigationMode.WALKING.name) ?: NavigationMode.WALKING.name
        return try { NavigationMode.valueOf(modeStr) } catch (e: Exception) { NavigationMode.WALKING }
    }

    override fun getTappedPoint(): GpxPoint? {
        val hasTapped = prefs.getBoolean("has_tapped_point", false)
        return if (hasTapped) {
            val tLat = prefs.getFloat("tapped_point_lat", 0f).toDouble()
            val tLon = prefs.getFloat("tapped_point_lon", 0f).toDouble()
            GpxPoint(tLat, tLon)
        } else {
            null
        }
    }

    override fun getActiveRegionId(): String? = prefs.getString("active_region_id", null)
    override fun getActiveGpxId(): String? = prefs.getString("active_gpx_id", null)

    override fun setMapCenterLat(lat: Double) { prefs.edit().putFloat("map_center_lat", lat.toFloat()).apply() }
    override fun setMapCenterLon(lon: Double) { prefs.edit().putFloat("map_center_lon", lon.toFloat()).apply() }
    override fun setMapZoomLevel(zoom: Int) { prefs.edit().putInt("map_zoom_level", zoom).apply() }
    override fun setMapFollowLocation(follow: Boolean) { prefs.edit().putBoolean("map_follow_location", follow).apply() }
    override fun setMapTheme(theme: MapTheme) { prefs.edit().putString("map_theme", theme.name).apply() }
    override fun setNavigationAlertMode(mode: NavigationAlertMode) { prefs.edit().putString("nav_alert_mode", mode.name).apply() }
    override fun setNavigationMode(mode: NavigationMode) { prefs.edit().putString("navigation_mode", mode.name).apply() }

    override fun setTappedPoint(point: GpxPoint?) {
        val editor = prefs.edit()
        if (point != null) {
            editor.putBoolean("has_tapped_point", true)
                .putFloat("tapped_point_lat", point.lat.toFloat())
                .putFloat("tapped_point_lon", point.lon.toFloat())
        } else {
            editor.putBoolean("has_tapped_point", false)
        }
        editor.apply()
    }

    override fun setActiveRegionId(id: String?) {
        if (id == null) prefs.edit().remove("active_region_id").apply()
        else prefs.edit().putString("active_region_id", id).apply()
    }

    override fun setActiveGpxId(id: String?) {
        if (id == null) prefs.edit().remove("active_gpx_id").apply()
        else prefs.edit().putString("active_gpx_id", id).apply()
    }
}

