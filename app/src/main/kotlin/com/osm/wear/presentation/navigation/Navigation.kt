package com.osm.wear.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.osm.wear.presentation.screens.DownloadScreen
import com.osm.wear.presentation.screens.GpxScreen
import com.osm.wear.presentation.screens.MapScreen
import com.osm.wear.presentation.screens.MapViewModel
import com.osm.wear.presentation.screens.MenuScreen

object Routes {
    const val MENU = "menu"
    const val MAP = "map"
    const val DOWNLOAD = "download"
    const val GPX = "gpx"
}

@Composable
fun OsmWearNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    // Single shared ViewModel across all screens
    val viewModel: MapViewModel = viewModel()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.MAP
    ) {
        composable(Routes.MAP) {
            MapScreen(
                viewModel = viewModel,
                onOpenMenu = { navController.navigate(Routes.MENU) }
            )
        }
        composable(Routes.MENU) {
            MenuScreen(
                onNavigateToMap = { navController.navigate(Routes.MAP) },
                onNavigateToDownload = { navController.navigate(Routes.DOWNLOAD) },
                onNavigateToGpx = { navController.navigate(Routes.GPX) }
            )
        }
        composable(Routes.DOWNLOAD) {
            DownloadScreen(viewModel = viewModel)
        }
        composable(Routes.GPX) {
            GpxScreen(viewModel = viewModel)
        }
    }
}
