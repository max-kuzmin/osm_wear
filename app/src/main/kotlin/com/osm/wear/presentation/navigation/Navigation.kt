package com.osm.wear.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.osm.wear.presentation.screens.*
import com.osm.wear.view_models.*
import androidx.compose.runtime.LaunchedEffect

object Routes {
    const val MAP              = "map"
    const val MAIN_MENU        = "main_menu"
    const val REGIONS          = "regions"
    const val GPX_TRACKS       = "gpx_tracks"
    const val MARKERS          = "markers"
    const val PREFERENCES      = "preferences"
    const val SEARCH_ADDRESS   = "search_address"
}

@Composable
fun OsmWearNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    
    // Instantiate all view models at the nav graph scope to share their states across screens
    val mapVm: MapViewModel = hiltViewModel()
    val markerVm: MarkerViewModel = hiltViewModel()
    val navVm: NavigationViewModel = hiltViewModel()
    val regionsVm: RegionsViewModel = hiltViewModel()
    val gpxVm: GpxFilesViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        navVm.navigationEvents.collect { route ->
            if (route == Routes.MAP) {
                navController.popBackStack(Routes.MAP, false)
            } else {
                navController.navigate(route)
            }
        }
    }

    // Connect location updates from map to navigation engine and marker view model
    LaunchedEffect(Unit) {
        mapVm.currentLocation.collect { loc ->
            if (loc != null) {
                navVm.updateLocation(loc)
                markerVm.updateCurrentLocation(loc)
            }
        }
    }
    
    // Connect settings battery mode to location tracking
    LaunchedEffect(Unit) {
        settingsVm.uiState.collect { state ->
            mapVm.startLocationTracking(state.gpsBatteryMode)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.MAP,
        userSwipeEnabled = currentRoute != Routes.MAP
    ) {
        composable(Routes.MAP) {
            MainMapScreen(
                mapVm = mapVm,
                markerVm = markerVm,
                navVm = navVm,
                regionsVm = regionsVm,
                gpxVm = gpxVm,
                settingsVm = settingsVm,
                onOpenMenu = { navController.navigate(Routes.MAIN_MENU) }
            )
        }
        composable(Routes.MAIN_MENU) {
            val context = androidx.compose.ui.platform.LocalContext.current
            MainMenuScreen(
                gpxVm = gpxVm,
                navVm = navVm,
                onStartNavigation = { gpx ->
                    val error = navVm.startNavigation(gpx, mapVm.currentLocation.value) { newMode ->
                        settingsVm.setGpsBatteryMode(newMode)
                    }
                    if (error != null) {
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        navController.popBackStack(Routes.MAP, false)
                    }
                },
                onStopNavigation = {
                    navVm.stopNavigation { newMode -> settingsVm.setGpsBatteryMode(newMode) }
                },
                onOpenGpxTracks = { navController.navigate(Routes.GPX_TRACKS) },
                onOpenMarkers = { navController.navigate(Routes.MARKERS) },
                onOpenPreferences = { navController.navigate(Routes.PREFERENCES) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REGIONS) {
            RegionsScreen(
                regionsVm = regionsVm,
                onRegionSelected      = { navController.popBackStack(Routes.MAP, false) },
                onBack                = { navController.popBackStack() }
            )
        }
        composable(Routes.GPX_TRACKS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            GpxTracksScreen(
                gpxVm = gpxVm,
                navVm = navVm,
                settingsVm = settingsVm,
                onStartNavigation = { gpx ->
                    val error = navVm.startNavigation(gpx, mapVm.currentLocation.value) { newMode ->
                        settingsVm.setGpsBatteryMode(newMode)
                    }
                    if (error != null) {
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        navController.popBackStack(Routes.MAP, false)
                    }
                },
                onStopNavigation  = { 
                    navVm.stopNavigation { newMode -> settingsVm.setGpsBatteryMode(newMode) }
                },
                onBack            = { navController.popBackStack() }
            )
        }
        composable(Routes.MARKERS) {
            MarkersScreen(
                navVm = navVm,
                markerVm = markerVm,
                mapVm = mapVm,
                settingsVm = settingsVm,
                gpxVm = gpxVm,
                onOpenSearch = {
                    navController.navigate(Routes.SEARCH_ADDRESS)
                },
                onStartNavigation = {
                    navController.popBackStack(Routes.MAP, false)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PREFERENCES) {
            PreferencesScreen(
                settingsVm = settingsVm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH_ADDRESS) {
            SearchAddressScreen(
                markerVm = markerVm,
                mapVm = mapVm,
                onAddressSelected = {
                    navController.popBackStack(Routes.MAP, false)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
