package com.osm.wear.view_models

import com.osm.wear.models.DownloadState
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.MapRegion

data class RegionsUiState(
    val downloadedRegions: List<DownloadedRegion> = emptyList(),
    val groupedRegions: Map<String, List<MapRegion>> = emptyMap(),
    val downloadState: DownloadState = DownloadState.Idle
)
