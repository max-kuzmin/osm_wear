package com.osm.wear.models.track_to_map

import com.osm.wear.models.GpxPoint

/**
 * Result of matching a GPX track to the road network.
 */
data class MatchedTurn(
    val point: GpxPoint,
    val bearingChange: Float,
    val roadNameBefore: String?,
    val roadNameAfter: String?,
    val distanceFromStartM: Double
)