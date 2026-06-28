package com.osm.wear.view_models

import com.osm.wear.models.GpxFile

data class GpxUiState(
    val gpxFiles: List<GpxFile> = emptyList(),
    val activeGpxFile: GpxFile? = null,
    val isActiveGpxCovered: Boolean = false,
    val isSaving: Boolean = false
)
