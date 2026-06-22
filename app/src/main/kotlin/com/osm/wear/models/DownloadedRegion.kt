package com.osm.wear.models

data class DownloadedRegion(
    val region: MapRegion,
    val filePath: String,
    val fileSizeMb: Int,
    val isActive: Boolean
)

