package com.osm.wear.view_models

import com.osm.wear.models.GpxFile
import com.osm.wear.models.NavigationState

data class MainMenuUiState(
    val activeGpxFile: GpxFile? = null,
    val navigationState: NavigationState? = null
)
