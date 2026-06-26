package com.osm.wear.models

data class NavigationWaypoint(
    val index: Int,
    val rawIndex: Int = index,
    val point: GpxPoint,
    val bearingToNext: Float,
    val distanceToNextM: Float,
    val isTurn: Boolean,
    val turnBearingChange: Float = 0f,
    val roadName: String? = null,
    val isIntersection: Boolean = false
)
