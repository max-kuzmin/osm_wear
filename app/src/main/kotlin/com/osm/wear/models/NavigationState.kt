package com.osm.wear.models

data class NavigationState(
    val isActive: Boolean = false,
    val gpxFile: GpxFile? = null,
    val waypoints: List<NavigationWaypoint> = emptyList(),
    val currentWaypointIndex: Int = 0,
    val distanceToNextTurnM: Float = 0f,
    val bearingToNextTurn: Float = 0f,
    val totalRemainingM: Float = 0f,
    val isOffTrack: Boolean = false,
    val lastAlertedWaypointIndex: Int = -1
)

