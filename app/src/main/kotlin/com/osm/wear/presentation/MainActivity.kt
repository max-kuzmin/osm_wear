package com.osm.wear.presentation

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.osm.wear.presentation.navigation.AppNavGraph
import com.osm.wear.services.IUiRouter
import com.osm.wear.presentation.theme.OsmWearTheme
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.services.INavigationTrackingService
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var gpxRepository: IGpxRepository

    @Inject
    lateinit var navigationTrackingService: INavigationTrackingService

    @Inject
    lateinit var uiNavigationManager: IUiRouter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            // Location permission handled by map
        }
        android.util.Log.d("MainActivity", "Location permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep screen on for map display
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            OsmWearTheme {
                AppNavGraph(navigationTrackingService, uiNavigationManager)
            }
        }

        requestPermissions()
        handleGpxIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGpxIntent(intent)
    }

    private fun requestPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun handleGpxIntent(intent: android.content.Intent?) {
        if (intent != null && intent.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    android.content.Intent.EXTRA_STREAM,
                    android.net.Uri::class.java
                )
            } else {
                data
            }
            if (uri != null) {
                android.util.Log.d("MainActivity", "Handling intent to import GPX from: $uri")
                lifecycleScope.launch {
                    gpxRepository.importFromUri(uri).onSuccess { gpx ->
                        gpxRepository.setActive(gpx.id)
                    }
                }
                uiNavigationManager.routeTo(com.osm.wear.models.enums.Routes.MAP.value)
            }
        }
    }
}
