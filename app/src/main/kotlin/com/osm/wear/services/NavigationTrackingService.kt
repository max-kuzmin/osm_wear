package com.osm.wear.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.osm.wear.models.GpxFile
import com.osm.wear.models.NavigationState
import com.osm.wear.models.UserLocation
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.repositories.IAlertsRepository
import com.osm.wear.repositories.ICursorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationTrackingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buildInitialNavigationStateUseCase: BuildInitialNavigationStateUseCase,
    private val updateNavigationStateUseCase: UpdateNavigationStateUseCase,
    private val alertsRepository: IAlertsRepository,
    private val cursorRepository: ICursorRepository
) : INavigationTrackingService {

    private val _navigationState = MutableStateFlow<NavigationState?>(null)
    override val navigationState: StateFlow<NavigationState?> = _navigationState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationJob: Job? = null

    override fun startNavigation(
        gpx: GpxFile,
        initialLocation: UserLocation?
    ): String? {
        val result = buildInitialNavigationStateUseCase(gpx)
        if (result.isFailure) {
            return result.exceptionOrNull()?.message ?: "Failed to build navigation state"
        }

        var nav = result.getOrThrow()
        
        initialLocation?.let { loc ->
            nav = updateNavigationStateUseCase(nav, loc)
        }
        
        _navigationState.value = nav
        
        startLocationTracking()

        alertsRepository.announce("Navigation started")
        
        // Start Android Foreground Service directly
        val serviceIntent = Intent(context, NavigationForegroundService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
        
        return null // success
    }

    override fun stopNavigation() {
        stopLocationTracking()
        _navigationState.value = null
        
        alertsRepository.announce("Navigation stopped")
        
        // Stop Android Foreground Service directly
        val serviceIntent = Intent(context, NavigationForegroundService::class.java)
        context.stopService(serviceIntent)
    }

    private fun updateLocation(loc: UserLocation) {
        _navigationState.value?.let { nav ->
            val updated = updateNavigationStateUseCase(nav, loc)
            _navigationState.value = updated
            if (!updated.isActive) {
                stopNavigation()
            }
        }
    }

    private fun startLocationTracking() {
        locationJob?.cancel()
        locationJob = scope.launch {
            cursorRepository.locationFlow(GpsBatteryMode.HIGH_ACCURACY).collect { loc ->
                updateLocation(loc)
            }
        }
    }

    private fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
    }
}
