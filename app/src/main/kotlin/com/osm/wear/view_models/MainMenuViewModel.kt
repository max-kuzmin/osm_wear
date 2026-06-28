package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import com.osm.wear.models.GpxFile
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.services.INavigationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    private val gpxRepository: IGpxRepository,
    private val navigationTrackingService: INavigationTrackingService
) : ViewModel() {

    val activeGpxFile: StateFlow<GpxFile?> = gpxRepository.activeGpxFile
    val navigationState = navigationTrackingService.navigationState

    fun startNavigation(gpx: GpxFile, initialLocation: UserLocation?): String? {
        return navigationTrackingService.startNavigation(gpx, initialLocation)
    }

    fun stopNavigation() {
        navigationTrackingService.stopNavigation()
    }
}
