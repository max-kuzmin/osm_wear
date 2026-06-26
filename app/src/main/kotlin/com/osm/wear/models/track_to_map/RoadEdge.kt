package com.osm.wear.models.track_to_map

import com.osm.wear.models.GpxPoint

/**
 * An edge in the road graph, representing a road segment between two nodes.
 */
data class RoadEdge(
    val id: Int,
    val fromNodeId: Int,
    val toNodeId: Int,
    val points: List<GpxPoint>,
    val lengthM: Double,
    val roadName: String?,
    val highwayType: String
)
