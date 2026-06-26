package com.osm.wear.models

data class SegmentProjection(
    val projectedPoint: GpxPoint,
    val distanceToSegmentM: Double,
    val fraction: Double
)
