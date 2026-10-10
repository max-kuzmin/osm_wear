package com.osm.wear.services

import com.osm.wear.models.RouteFile
import com.osm.wear.models.UserLocation
import com.osm.wear.models.NavigationState
import kotlinx.coroutines.flow.StateFlow

interface INavigationTrackingService {
    val navigationState: StateFlow<NavigationState?>
    fun startNavigation(route: RouteFile, initialLocation: UserLocation?): String?
    fun stopNavigation(announce: Boolean = true)
}
