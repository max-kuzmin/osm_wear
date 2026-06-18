package com.osm.wear.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.osm.wear.presentation.screens.*

object Routes {
    const val MAP              = "map"
    const val SETTINGS         = "settings"
    const val REGIONS          = "regions"
    const val GPX_FILES        = "gpx_files"
}

@Composable
fun OsmWearNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    val vm: MapViewModel = hiltViewModel()



    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.MAP
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
                onOpenRegions     = { navController.navigate(Routes.REGIONS) },
                onOpenGpxFiles    = { navController.navigate(Routes.GPX_FILES) },
                onBack            = { navController.popBackStack() }
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
    }
}
