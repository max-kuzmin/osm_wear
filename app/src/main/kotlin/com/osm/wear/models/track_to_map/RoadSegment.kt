package com.osm.wear.models.track_to_map

import com.osm.wear.models.GpxPoint

/**
 * A road segment extracted from the Mapsforge .map file.
 */
data class RoadSegment(
    val id: Long,
    val points: List<GpxPoint>,
    val highwayType: String,
    val name: String?,
    val oneway: Boolean
)
