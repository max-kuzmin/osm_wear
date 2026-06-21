package com.osm.wear.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.osm.wear.presentation.screens.*

object Routes {
    const val MAP              = "map"
    const val SETTINGS         = "settings"
    const val REGIONS          = "regions"
    const val GPX_FILES        = "gpx_files"
    const val PATH_FINDER      = "path_finder"
}

@Composable
fun OsmWearNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    val vm: MapViewModel = hiltViewModel()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.navigationEvents.collect { route ->
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
            MainMapScreen(
                vm = vm,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = vm,
                onOpenRegions          = { navController.navigate(Routes.REGIONS) },
                onOpenGpxFiles         = { navController.navigate(Routes.GPX_FILES) },
                onOpenPathFinder       = { navController.navigate(Routes.PATH_FINDER) },
                onBack                 = { navController.popBackStack() }
            )
        }
        composable(Routes.REGIONS) {
            RegionsScreen(
                vm = vm,
                onRegionSelected      = { navController.popBackStack(Routes.MAP, false) },
                onBack                = { navController.popBackStack() }
            )
        }
        composable(Routes.GPX_FILES) {
            GpxFilesScreen(
                vm = vm,
                onStartNavigation = {
                    vm.startNavigation()
                    navController.popBackStack(Routes.MAP, false)
                },
                onStopNavigation  = { vm.stopNavigation() },
                onBack            = { navController.popBackStack() }
            )
        }
        composable(Routes.PATH_FINDER) {
            PathFinderScreen(
                vm = vm,
                onStartNavigation = {
                    navController.popBackStack(Routes.MAP, false)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
