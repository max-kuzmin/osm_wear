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
    val lastAlertedWaypointIndex: Int = -1,
    val lastNextTurnIndex: Int = -1,
    val warnedRightAfterPrevious: Boolean = false,
    val warned1km: Boolean = false,
    val warned300m: Boolean = false,
    val warnedDuringTurn: Boolean = false
)
