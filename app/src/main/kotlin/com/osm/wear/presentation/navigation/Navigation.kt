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
    const val SETTINGS         = "settings"
    const val REGIONS          = "regions"
    const val GPX_FILES        = "gpx_files"
    const val PATH_FINDER      = "path_finder"
    const val SEARCH_ADDRESS   = "search_address"
}

@Composable
fun OsmWearNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    
    // Instantiate all view models at the nav graph scope to share their states across screens
    val mapVm: MapViewModel = hiltViewModel()
    val dotMarkVm: DotMarkViewModel = hiltViewModel()
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

    // Connect location updates from map to navigation engine and dot mark view model
    LaunchedEffect(Unit) {
        mapVm.currentLocation.collect { loc ->
            if (loc != null) {
                navVm.updateLocation(loc)
                dotMarkVm.updateCurrentLocation(loc)
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
                dotMarkVm = dotMarkVm,
                navVm = navVm,
                regionsVm = regionsVm,
                gpxVm = gpxVm,
                settingsVm = settingsVm,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsVm = settingsVm,
                dotMarkVm = dotMarkVm,
                regionsVm = regionsVm,
                gpxVm = gpxVm,
                onOpenRegions          = { navController.navigate(Routes.REGIONS) },
                onOpenGpxFiles         = { navController.navigate(Routes.GPX_FILES) },
                onOpenPathFinder       = { navController.navigate(Routes.PATH_FINDER) },
                onBack                 = { navController.popBackStack() }
            )
        }
        composable(Routes.REGIONS) {
            RegionsScreen(
                regionsVm = regionsVm,
                onRegionSelected      = { navController.popBackStack(Routes.MAP, false) },
                onBack                = { navController.popBackStack() }
            )
        }
        composable(Routes.GPX_FILES) {
            GpxFilesScreen(
                gpxVm = gpxVm,
                navVm = navVm,
                settingsVm = settingsVm,
                onStartNavigation = { gpx ->
                    navVm.startNavigation(gpx, mapVm.currentLocation.value) { newMode ->
                        settingsVm.setGpsBatteryMode(newMode)
                    }
                    navController.popBackStack(Routes.MAP, false)
                },
                onStopNavigation  = { 
                    navVm.stopNavigation { newMode -> settingsVm.setGpsBatteryMode(newMode) }
                },
                onBack            = { navController.popBackStack() }
            )
        }
        composable(Routes.PATH_FINDER) {
            PathFinderScreen(
                navVm = navVm,
                dotMarkVm = dotMarkVm,
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
        composable(Routes.SEARCH_ADDRESS) {
            SearchAddressScreen(
                dotMarkVm = dotMarkVm,
                onAddressSelected = {
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
