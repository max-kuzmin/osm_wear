package com.osm.wear.models

data class NavigationWaypoint(
    val index: Int,
    val point: GpxPoint,
    val bearingToNext: Float,
    val distanceToNextM: Float,
    val isTurn: Boolean,
    val turnBearingChange: Float = 0f
)

