package com.osm.wear.view_models

import com.osm.wear.models.DownloadState
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.MapRegion

data class RegionsUiState(
    val downloadedRegions: List<DownloadedRegion> = emptyList(),
    val groupedRegions: Map<String, List<MapRegion>> = emptyMap(),
    val downloadState: DownloadState = DownloadState.Idle,
    val validRegionIds: Set<String> = emptySet(),
    val freeRegionId: String? = null,
    val isFreeTrialExpired: Boolean = false,
    val productPrices: Map<String, String> = emptyMap(),
    val isMonetizationEnabled: Boolean = true
)
