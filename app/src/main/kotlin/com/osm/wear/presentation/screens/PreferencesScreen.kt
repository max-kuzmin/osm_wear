package com.osm.wear.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.presentation.theme.AppDimensions
import com.osm.wear.view_models.PreferencesViewModel

@Composable
fun PreferencesScreen(
    settingsVm: PreferencesViewModel,
    onBack: () -> Unit
) {
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            vertical = AppDimensions.ScreenVerticalPadding,
            horizontal = AppDimensions.ScreenHorizontalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.ListSpacingNormal)
    ) {
        item {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // 1. Map Theme
        item {
            Button(
                onClick = {
                    val nextTheme = when (settingsState.mapTheme) {
                        MapTheme.BIKER -> MapTheme.DARK
                        MapTheme.DARK -> MapTheme.DEFAULT
                        MapTheme.DEFAULT -> MapTheme.INDIGO
                        MapTheme.INDIGO -> MapTheme.MOTORIDER
                        MapTheme.MOTORIDER -> MapTheme.OSMARENDER
                        MapTheme.OSMARENDER -> MapTheme.BIKER
                    }
                    settingsVm.setMapTheme(nextTheme)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Map Theme",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Map Theme", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    val themeText = when (settingsState.mapTheme) {
                        MapTheme.BIKER -> "Biker"
                        MapTheme.DARK -> "Dark"
                        MapTheme.DEFAULT -> "Default"
                        MapTheme.INDIGO -> "Indigo"
                        MapTheme.MOTORIDER -> "Motorider"
                        MapTheme.OSMARENDER -> "Osmarender"
                    }
                    Text(
                        text = themeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        // 2. Navigation Mode (Travel Mode)
        item {
            val modeIcon = when (settingsState.navigationMode) {
                NavigationMode.WALKING -> Icons.Default.DirectionsWalk
                NavigationMode.CYCLING -> Icons.Default.DirectionsBike
                NavigationMode.DRIVING -> Icons.Default.DirectionsCar
                NavigationMode.GPX_ONLY -> Icons.Default.LocationOn
            }
            Button(
                onClick = { settingsVm.cycleNavigationMode() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = "Navigation Mode",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Navigation Mode", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    Text(
                        text = settingsState.navigationMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        // 3. GPX Power Mode (GPS battery mode)
        item {
            Button(
                onClick = {
                    val nextMode = when (settingsState.gpsBatteryMode) {
                        GpsBatteryMode.HIGH_ACCURACY -> GpsBatteryMode.BALANCED
                        GpsBatteryMode.BALANCED      -> GpsBatteryMode.LOW_POWER
                        GpsBatteryMode.LOW_POWER     -> GpsBatteryMode.HIGH_ACCURACY
                    }
                    settingsVm.setGpsBatteryMode(nextMode)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "GPX Power Mode",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("GPX Power Mode", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    Text(
                        text = settingsState.gpsBatteryMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        // 4. Navigation Alerts
        item {
            Button(
                onClick = {
                    val nextMode = when (settingsState.navigationAlertMode) {
                        NavigationAlertMode.VOICE      -> NavigationAlertMode.SOUND
                        NavigationAlertMode.SOUND      -> NavigationAlertMode.VIBRATION
                        NavigationAlertMode.VIBRATION  -> NavigationAlertMode.SILENT
                        NavigationAlertMode.SILENT     -> NavigationAlertMode.VOICE
                    }
                    settingsVm.setNavigationAlertMode(nextMode)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Navigation Alerts",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Navigation Alerts", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    val modeText = when (settingsState.navigationAlertMode) {
                        NavigationAlertMode.VOICE      -> "Voice"
                        NavigationAlertMode.SOUND      -> "Sound"
                        NavigationAlertMode.VIBRATION  -> "Vibration"
                        NavigationAlertMode.SILENT     -> "Silent"
                    }
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        item {
            BackButton(onClick = onBack)
        }
    }
}
