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
import com.osm.wear.services.IUiRouter
import com.osm.wear.models.enums.Routes

@Composable
fun AppNavGraph(
    uiNavigationManager: IUiRouter
) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(Unit) {
        uiNavigationManager.routingEvents.collect { route ->
            if (route == Routes.MAP.value) {
                navController.popBackStack(Routes.MAP.value, false)
            } else {
                navController.navigate(route)
            }
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.MAP.value,
        userSwipeEnabled = currentRoute != Routes.MAP.value
    ) {
        composable(Routes.MAP.value) {
            val mapVm: MapViewModel = hiltViewModel()
            MainMapScreen(
                mapVm = mapVm,
                onOpenMenu = { navController.navigate(Routes.MAIN_MENU.value) }
            )
        }
        composable(Routes.MAIN_MENU.value) {
            val menuVm: MainMenuViewModel = hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            MainMenuScreen(
                menuVm = menuVm,
                onNavigationStarted = {
                    navController.popBackStack(Routes.MAP.value, false)
                },
                onOpenGpxTracks = { navController.navigate(Routes.GPX_TRACKS.value) },
                onOpenMarkers = { navController.navigate(Routes.MARKERS.value) },
                onOpenPreferences = { navController.navigate(Routes.PREFERENCES.value) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REGIONS.value) {
            val regionsVm: RegionsViewModel = hiltViewModel()
            RegionsScreen(
                regionsVm = regionsVm,
                onRegionSelected      = { navController.popBackStack(Routes.MAP.value, false) },
                onBack                = { navController.popBackStack() }
            )
        }
        composable(Routes.GPX_TRACKS.value) {
            val gpxVm: GpxTracksViewModel = hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            GpxTracksScreen(
                gpxVm = gpxVm,
                onBack            = { navController.popBackStack() }
            )
        }
        composable(Routes.MARKERS.value) {
            val markersVm: MarkersViewModel = hiltViewModel()
            MarkersScreen(
                markersVm = markersVm,
                onOpenSearch = {
                    navController.navigate(Routes.SEARCH_ADDRESS.value)
                },
                onNavigateToMap = {
                    navController.popBackStack(Routes.MAP.value, false)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PREFERENCES.value) {
            val preferencesVm: PreferencesViewModel = hiltViewModel()
            PreferencesScreen(
                settingsVm = preferencesVm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH_ADDRESS.value) {
            val searchVm: SearchAddressViewModel = hiltViewModel()
            SearchAddressScreen(
                searchVm = searchVm,
                onAddressSelected = {
                    navController.popBackStack(Routes.MAP.value, false)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
