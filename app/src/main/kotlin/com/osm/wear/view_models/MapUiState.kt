package com.osm.wear.view_models

import com.osm.wear.models.*
import java.io.File

data class MapUiState(
    /** The .map file currently rendered on the main screen, or null if none. */
    val activeMapFile: File? = null,
    /** ID of the active region (used to highlight in the regions list). */
    val activeRegionId: String? = null,
    /** The GPX file currently overlaid on the map, or null if none. */
    val activeGpxFile: GpxFile? = null,
    /** Current zoom level (3..20). */
    val zoomLevel: Int = 14,
    /** Map centre latitude. */
    val centerLat: Double = 0.0,
    /** Map centre longitude. */
    val centerLon: Double = 0.0,
    /** Whether the map should snap to the user's current location. */
    val followLocation: Boolean = true,
    /** Current navigation state, or null when not navigating. */
    val navigationState: NavigationState? = null,
    /** Battery mode for GPS. */
    val gpsBatteryMode: GpsBatteryMode = GpsBatteryMode.BALANCED,
    /** Map rotation mode (North-up or Heading-up). */
    val mapRotationMode: MapRotationMode = MapRotationMode.NORTH_UP,
    /** Current manual rotation angle, used when MapRotationMode is MANUAL. */
    val manualRotation: Float = 0f,
    /** Map style theme. */
    val mapTheme: MapTheme = MapTheme.OSMARENDER,
    /** Navigation alert mode (Voice, Sound, Vibration, Silent). */
    val navigationAlertMode: NavigationAlertMode = NavigationAlertMode.VOICE,
    /** Tapped point coordinate (red dot) */
    val tappedPoint: GpxPoint? = null,
    /** Mode for point-to-point navigation (Walking, Cycling, Driving) */
    val navigationMode: NavigationMode = NavigationMode.WALKING
)

