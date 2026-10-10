package com.osm.wear.view_models

import com.osm.wear.models.MapRegion
import com.osm.wear.models.NavigationState
import com.osm.wear.models.RouteFile

data class MainMenuUiState(
    val activeRoute: RouteFile? = null,
    val navigationState: NavigationState? = null,
    val activeRegion: MapRegion? = null
)
