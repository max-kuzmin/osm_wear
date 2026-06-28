package com.osm.wear.view_models

import com.osm.wear.models.enums.MapRotationMode
import com.osm.wear.models.enums.MapTheme

import com.osm.wear.models.GpxPoint

data class MapUiState(
    /** Current zoom level (3..20). */
    val zoomLevel: Int = 14,
    /** Map centre latitude. */
    val centerLat: Double = 0.0,
    /** Map centre longitude. */
    val centerLon: Double = 0.0,
    /** Whether the map should snap to the user's current location. */
    val followLocation: Boolean = true,
    /** Map rotation mode (North-up, Heading-up, or Manual). */
    val mapRotationMode: MapRotationMode = MapRotationMode.NORTH_UP,
    /** Current manual rotation angle, used when MapRotationMode is MANUAL. */
    val manualRotation: Float = 0f,
    /** The active theme of the map view. */
    val mapTheme: MapTheme = MapTheme.DEFAULT,
    /** Tapped point coordinate (blue dot mark) */
    val tappedPoint: GpxPoint? = null,
    /** Tapped point name/object name */
    val tappedPointName: String? = null,
    /** Tapped point address string */
    val tappedPointAddress: String? = null,
    /** Whether reverse geocoding is currently in progress */
    val isResolvingAddress: Boolean = false,
    
    // Derived from repositories
    val currentLocation: com.osm.wear.models.UserLocation? = null,
    val activeMapFile: java.io.File? = null,
    val activeGpxFile: com.osm.wear.models.GpxFile? = null,
    val navigationState: com.osm.wear.models.NavigationState? = null
)
