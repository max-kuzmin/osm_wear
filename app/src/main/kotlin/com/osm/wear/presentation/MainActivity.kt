package com.osm.wear.presentation

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.osm.wear.presentation.navigation.OsmWearNavGraph
import com.osm.wear.presentation.theme.OsmWearTheme

class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled; location tracking starts in MapScreen LaunchedEffect
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        android.util.Log.d("MainActivity", "Location permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Request location permission on startup
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            OsmWearTheme {
                OsmWearNavGraph()
            }
        }
    }
}
