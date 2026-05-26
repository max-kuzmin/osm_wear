package com.osm.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun OsmWearTheme(content: @Composable () -> Unit) {
    // Wear OS Material3 uses device-defined color scheme by default.
    // We rely on the system dark theme which is standard on Wear OS.
    MaterialTheme(content = content)
}
