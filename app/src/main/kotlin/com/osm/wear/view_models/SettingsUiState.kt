package com.osm.wear.view_models

import com.osm.wear.models.MapTheme
import com.osm.wear.models.NavigationAlertMode
import com.osm.wear.models.NavigationMode
import com.osm.wear.models.GpsBatteryMode

data class SettingsUiState(
    val mapTheme: MapTheme = MapTheme.DEFAULT,
    val navigationAlertMode: NavigationAlertMode = NavigationAlertMode.VOICE,
    val navigationMode: NavigationMode = NavigationMode.WALKING,
    val gpsBatteryMode: GpsBatteryMode = GpsBatteryMode.BALANCED
)
