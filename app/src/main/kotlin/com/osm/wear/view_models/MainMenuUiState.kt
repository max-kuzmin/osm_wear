package com.osm.wear.view_models

import com.osm.wear.models.GpxFile
import com.osm.wear.models.NavigationState

import com.osm.wear.models.MapRegion

data class MainMenuUiState(
    val activeGpxFile: GpxFile? = null,
    val navigationState: NavigationState? = null,
    val activeRegion: MapRegion? = null
)
