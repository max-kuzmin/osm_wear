package com.osm.wear.repositories

import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.UserLocation
import com.osm.wear.models.NavigationState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

interface INavigationRepository {
    val navigationState: StateFlow<NavigationState?>
    val navigationEvents: SharedFlow<String>
    fun startNavigation(gpx: GpxFile, initialLocation: UserLocation?): String?
    fun stopNavigation()
    fun updateLocation(loc: UserLocation)
    fun navigateTo(route: String)
    fun startNavigationToPoint(
        target: GpxPoint,
        currentLoc: UserLocation?,
        onGpxCreated: (GpxFile) -> Unit,
        onFailure: (String) -> Unit
    )
}
