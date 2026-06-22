package com.osm.wear.services

import com.osm.wear.models.GpxFile
import com.osm.wear.models.NavigationAlertMode
import com.osm.wear.models.NavigationState
import com.osm.wear.models.UserLocation

interface INavigationService {
    fun startForegroundService()
    fun stopForegroundService()
    fun setAlertMode(mode: NavigationAlertMode)
    fun announce(message: String)
    fun release()
    fun buildInitialNavigationState(gpx: GpxFile): NavigationState?
    fun updateNavigationState(state: NavigationState, location: UserLocation): NavigationState
}

