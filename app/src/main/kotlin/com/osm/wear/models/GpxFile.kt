package com.osm.wear.models

data class GpxFile(
    val id: String,
    val name: String,
    val filePath: String,
    val trackPoints: List<GpxPoint>,
    val totalDistanceKm: Double,
    val isActive: Boolean
)