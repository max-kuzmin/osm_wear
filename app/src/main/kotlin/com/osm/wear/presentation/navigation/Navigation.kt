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
import com.osm.wear.repositories.INavigationRepository

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
fun OsmWearNavGraph(navRepository: INavigationRepository) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(Unit) {
        navRepository.navigationEvents.collect { route ->
            if (route == Routes.MAP) {
                navController.popBackStack(Routes.MAP, false)
            } else {
                navController.navigate(route)
            }
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
            val mapVm: MapViewModel = hiltViewModel()
            MainMapScreen(
                mapVm = mapVm,
                onOpenMenu = { navController.navigate(Routes.MAIN_MENU) }
            )
        }
        composable(Routes.MAIN_MENU) {
            val menuVm: MainMenuViewModel = hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            MainMenuScreen(
                menuVm = menuVm,
                onStartNavigation = { gpx ->
                    val error = menuVm.startNavigation(gpx, null)
                    if (error != null) {
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        navController.popBackStack(Routes.MAP, false)
                    }
                },
                onStopNavigation = {
                    menuVm.stopNavigation()
                },
                onOpenGpxTracks = { navController.navigate(Routes.GPX_TRACKS) },
                onOpenMarkers = { navController.navigate(Routes.MARKERS) },
                onOpenPreferences = { navController.navigate(Routes.PREFERENCES) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REGIONS) {
            val regionsVm: RegionsViewModel = hiltViewModel()
            RegionsScreen(
                regionsVm = regionsVm,
                onRegionSelected      = { navController.popBackStack(Routes.MAP, false) },
                onBack                = { navController.popBackStack() }
            )
        }
        composable(Routes.GPX_TRACKS) {
            val gpxVm: GpxTracksViewModel = hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            GpxTracksScreen(
                gpxVm = gpxVm,
                onBack            = { navController.popBackStack() }
            )
        }
        composable(Routes.MARKERS) {
            val markersVm: MarkersViewModel = hiltViewModel()
            MarkersScreen(
                markersVm = markersVm,
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
            val preferencesVm: PreferencesViewModel = hiltViewModel()
            PreferencesScreen(
                settingsVm = preferencesVm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH_ADDRESS) {
            val searchVm: SearchAddressViewModel = hiltViewModel()
            SearchAddressScreen(
                searchVm = searchVm,
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
