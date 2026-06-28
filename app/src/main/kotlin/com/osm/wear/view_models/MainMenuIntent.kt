package com.osm.wear.view_models

import com.osm.wear.models.GpxFile
import com.osm.wear.models.UserLocation

sealed class MainMenuIntent {
    data class StartNavigation(val gpx: GpxFile, val initialLocation: UserLocation?) : MainMenuIntent()
    data object StopNavigation : MainMenuIntent()
}
