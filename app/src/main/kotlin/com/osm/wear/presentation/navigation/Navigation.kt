package com.osm.wear.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.osm.wear.presentation.screens.*

object Routes {
    const val MAP        = "map"
    const val MENU       = "menu"
    const val DOWNLOAD   = "download"
    const val GPX        = "gpx"
    const val RECORD     = "record"
    const val NAVIGATION = "navigation"
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
                onNavigateToMap      = { navController.popBackStack(Routes.MAP, false) },
                onNavigateToDownload = { navController.navigate(Routes.DOWNLOAD) },
                onNavigateToGpx      = { navController.navigate(Routes.GPX) },
                onNavigateToRecord   = { navController.navigate(Routes.RECORD) },
                onNavigateToNavigation = { navController.navigate(Routes.NAVIGATION) }
            )
        }
        composable(Routes.DOWNLOAD) {
            DownloadScreen(viewModel = viewModel)
        }
        composable(Routes.GPX) {
            GpxScreen(
                viewModel = viewModel,
                onNavigate = { navController.navigate(Routes.NAVIGATION) }
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.NAVIGATION) {
            NavigationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
