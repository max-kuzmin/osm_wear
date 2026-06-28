package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import com.osm.wear.models.GpxFile
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.repositories.INavigationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    private val gpxRepository: IGpxRepository,
    private val navigationRepository: INavigationRepository
) : ViewModel() {

    val activeGpxFile: StateFlow<GpxFile?> = gpxRepository.activeGpxFile
    val navigationState = navigationRepository.navigationState

    fun startNavigation(gpx: GpxFile, initialLocation: UserLocation?): String? {
        return navigationRepository.startNavigation(gpx, initialLocation)
    }

    fun stopNavigation() {
        navigationRepository.stopNavigation()
    }
}
