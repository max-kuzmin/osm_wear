package com.osm.wear.models

data class MapRegion(
    val id: String,          // e.g. "europe/germany"
    val name: String,
    val continent: String,
    val downloadUrl: String,
    val fileSizeMb: Int,
    val fileName: String     // e.g. "germany.map"
)

