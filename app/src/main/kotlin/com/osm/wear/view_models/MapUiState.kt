package com.osm.wear.view_models

import com.osm.wear.models.GpxPoint
import com.osm.wear.models.MapRotationMode

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
    /** Tapped point coordinate (red dot) */
    val tappedPoint: GpxPoint? = null
)
