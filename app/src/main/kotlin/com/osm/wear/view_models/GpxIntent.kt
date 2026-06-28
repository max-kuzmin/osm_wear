package com.osm.wear.view_models

import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint

sealed class GpxIntent {
    data object ScanFolders : GpxIntent()
    data class SetActive(val gpxFile: GpxFile) : GpxIntent()
    data class SaveCurrent(val name: String, val points: List<GpxPoint>) : GpxIntent()
}
