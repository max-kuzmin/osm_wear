package com.osm.wear.models

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val region: MapRegion,
        val progressPercent: Int,
        val downloadedMb: Float
    ) : DownloadState()
    data class Failed(val region: MapRegion, val error: String) : DownloadState()
}

