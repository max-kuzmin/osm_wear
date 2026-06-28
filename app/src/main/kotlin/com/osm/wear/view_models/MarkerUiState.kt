package com.osm.wear.view_models

import com.osm.wear.models.GpxPoint

data class MarkerUiState(
    /** Tapped point coordinate (blue dot mark) */
    val tappedPoint: GpxPoint? = null,
    /** Tapped point name/object name */
    val tappedPointName: String? = null,
    /** Tapped point address string */
    val tappedPointAddress: String? = null,
    /** Whether reverse geocoding is currently in progress */
    val isResolvingAddress: Boolean = false
)
