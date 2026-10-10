package com.osm.wear.view_models

import com.osm.wear.models.RouteFile
import com.osm.wear.models.UserLocation

sealed class MainMenuIntent {
    data class StartNavigation(val route: RouteFile, val initialLocation: UserLocation?) : MainMenuIntent()
    data object StopNavigation : MainMenuIntent()
}
