package com.osm.wear.presentation.screens

import com.osm.wear.view_models.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Notifications
import com.osm.wear.models.enums.GpsBatteryMode
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.presentation.components.BackButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.models.enums.MapTheme
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel,
    dotMarkVm: DotMarkViewModel,
    regionsVm: RegionsViewModel,
    gpxVm: GpxFilesViewModel,
    onOpenRegions: () -> Unit,
    onOpenGpxFiles: () -> Unit,
    onOpenPathFinder: () -> Unit,
    onBack: () -> Unit
) {
    val dotMarkState    by dotMarkVm.uiState.collectAsStateWithLifecycle()
    val settingsState   by settingsVm.uiState.collectAsStateWithLifecycle()
    val downloadedRegions by regionsVm.downloadedRegions.collectAsStateWithLifecycle()
    val gpxFiles        by gpxVm.gpxFiles.collectAsStateWithLifecycle()

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
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        item {
            val activeRegion = downloadedRegions.firstOrNull { it.isActive }
            Button(
                onClick = onOpenRegions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Map Regions", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = { 
                    Text(
                        text = activeRegion?.region?.name ?: "None selected", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ) 
                }
            )
        }

        item {
            val activeGpx = gpxFiles.firstOrNull { it.isActive }
            Button(
                onClick = onOpenGpxFiles,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("GPX Files", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = { 
                    Text(
                        text = activeGpx?.name ?: "None selected", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ) 
                }
            )
        }
 
         item {
            Button(
                onClick = onOpenPathFinder,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Path Finder", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    val tappedPoint = dotMarkState.tappedPoint
                    val pointText = if (tappedPoint != null) {
                        val name = dotMarkState.tappedPointName?.takeIf { it.isNotBlank() }
                        val addr = dotMarkState.tappedPointAddress?.takeIf { it.isNotBlank() }
                        val coords = "%.4f, %.4f".format(tappedPoint.lat, tappedPoint.lon)
                        buildString {
                            if (name != null) append(name)
                            if (addr != null) {
                                if (isNotEmpty()) append(" · ")
                                append(addr)
                            }
                            if (isEmpty()) append(coords)
                        }
                    } else {
                        "No point selected"
                    }
                    Text(
                        text = pointText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

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
                        contentDescription = null,
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
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("GPS Mode", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    Text(
                        text = settingsState.gpsBatteryMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

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
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Nav Alerts", style = MaterialTheme.typography.labelMedium) },
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
