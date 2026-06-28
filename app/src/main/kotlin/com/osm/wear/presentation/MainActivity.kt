package com.osm.wear.presentation

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.osm.wear.presentation.navigation.OsmWearNavGraph
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

        // Request permissions on startup
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            OsmWearTheme {
                OsmWearNavGraph(navigationTrackingService)
            }
        }

        // Navigation is now handled by a Foreground Service,
        // so the screen can be turned off to save battery.
        // We no longer keep the screen on artificially.

        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data
        if (action == android.content.Intent.ACTION_VIEW || action == android.content.Intent.ACTION_SEND) {
            val uri = if (action == android.content.Intent.ACTION_SEND) {
                androidx.core.content.IntentCompat.getParcelableExtra(
                    intent,
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
                navigationTrackingService.navigateTo(com.osm.wear.presentation.navigation.Routes.MAP)
            }
        }
    }
}


