package com.osm.wear.models

data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double = 0.0,
    val time: Long = 0L
)

// ─── Navigation ──────────────────────────────────────────────────────────────

