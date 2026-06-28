package com.osm.wear.repositories

import android.content.SharedPreferences
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.GpxPoint

class PreferencesRepository(
    private val prefs: SharedPreferences
) : IPreferencesRepository {

    override fun getMapCenter(): GpxPoint {
        val lat = prefs.getFloat("map_center_lat", 0.0f).toDouble()
        val lon = prefs.getFloat("map_center_lon", 0.0f).toDouble()
        return GpxPoint(lat, lon)
    }

    override fun setMapCenter(lat: Double, lon: Double) {
        prefs.edit()
            .putFloat("map_center_lat", lat.toFloat())
            .putFloat("map_center_lon", lon.toFloat())
            .apply()
    }

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

    override fun setMapZoomLevel(zoom: Int) { prefs.edit().putInt("map_zoom_level", zoom).apply() }
    override fun setMapFollowLocation(follow: Boolean) { prefs.edit().putBoolean("map_follow_location", follow).apply() }
    override fun setMapTheme(theme: MapTheme) { prefs.edit().putString("map_theme", theme.name).apply() }
    override fun setNavigationAlertMode(mode: NavigationAlertMode) { prefs.edit().putString("nav_alert_mode", mode.name).apply() }
    override fun setNavigationMode(mode: NavigationMode) { prefs.edit().putString("navigation_mode", mode.name).apply() }

    override fun getGpsBatteryMode(): GpsBatteryMode {
        val modeStr = prefs.getString("gps_battery_mode", GpsBatteryMode.BALANCED.name) ?: GpsBatteryMode.BALANCED.name
        return try { GpsBatteryMode.valueOf(modeStr) } catch (e: Exception) { GpsBatteryMode.BALANCED }
    }

    override fun setGpsBatteryMode(mode: GpsBatteryMode) {
        prefs.edit().putString("gps_battery_mode", mode.name).apply()
    }
}
