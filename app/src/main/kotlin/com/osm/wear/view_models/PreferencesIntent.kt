package com.osm.wear.view_models

import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationAlertMode

import com.osm.wear.models.enums.NavigationMode

sealed class PreferencesIntent {
    data class SetMapTheme(val theme: MapTheme) : PreferencesIntent()
    data class SetNavigationAlertMode(val mode: NavigationAlertMode) : PreferencesIntent()
    data class SetGpsBatteryMode(val mode: GpsBatteryMode) : PreferencesIntent()
    data class SetNavigationMode(val mode: NavigationMode) : PreferencesIntent()
}
