package com.osm.wear.view_models

import com.osm.wear.models.enums.GpsBatteryMode

sealed class MapIntent {
    data object LoadMapState : MapIntent()
    data class StartLocationTracking(val batteryMode: GpsBatteryMode) : MapIntent()
    data object CenterOnLocation : MapIntent()
    data object ZoomIn : MapIntent()
    data object ZoomOut : MapIntent()
    data class MapPanned(val lat: Double, val lon: Double) : MapIntent()
    data class PinchMoved(val newRotation: Float) : MapIntent()
    data class MapTapped(val lat: Double, val lon: Double) : MapIntent()
}
