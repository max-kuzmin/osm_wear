package com.osm.wear.models

import com.osm.wear.models.GpxPoint

data class SegmentProjection(
    val projectedPoint: GpxPoint,
    val distanceToSegmentM: Double,
    val fraction: Double
)

