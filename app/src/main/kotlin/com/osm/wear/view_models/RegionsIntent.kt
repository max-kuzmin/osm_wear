package com.osm.wear.view_models

import com.osm.wear.models.MapRegion

sealed class RegionsIntent {
    data object RefreshDownloadedRegions : RegionsIntent()
    data class SetActiveRegion(val region: MapRegion) : RegionsIntent()
    data class DeleteRegion(val region: MapRegion) : RegionsIntent()
    data class DownloadRegion(val region: MapRegion) : RegionsIntent()
    data object CancelDownload : RegionsIntent()
}
