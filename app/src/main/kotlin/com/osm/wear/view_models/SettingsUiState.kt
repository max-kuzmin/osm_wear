package com.osm.wear.view_models

import com.osm.wear.models.enums.MapTheme
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.GpsBatteryMode

data class SettingsUiState(
    val mapTheme: MapTheme = MapTheme.DEFAULT,
    val navigationAlertMode: NavigationAlertMode = NavigationAlertMode.VOICE,
    val navigationMode: NavigationMode = NavigationMode.WALKING,
    val gpsBatteryMode: GpsBatteryMode = GpsBatteryMode.BALANCED
)
