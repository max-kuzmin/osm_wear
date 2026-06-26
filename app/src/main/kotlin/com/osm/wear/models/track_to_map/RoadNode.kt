package com.osm.wear.models.track_to_map

import com.osm.wear.models.GpxPoint

/**
 * A node in the road graph, representing a point where roads meet or end.
 */
data class RoadNode(
    val id: Int,
    val point: GpxPoint,
    val edgeIds: MutableList<Int> = mutableListOf()
) {
    /** Number of distinct road edges meeting at this node. Degree ≥ 3 = intersection. */
    val degree: Int get() = edgeIds.size
}
