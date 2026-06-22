package com.osm.wear.presentation.screens

import com.osm.wear.view_models.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import com.osm.wear.presentation.components.BackButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.models.MapTheme
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
                        when {
                            !dotMarkState.tappedPointName.isNullOrBlank() -> dotMarkState.tappedPointName ?: ""
                            !dotMarkState.tappedPointAddress.isNullOrBlank() -> dotMarkState.tappedPointAddress ?: ""
                            else -> "%.4f, %.4f".format(tappedPoint.lat, tappedPoint.lon)
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
            BackButton(onClick = onBack)
        }
    }
}
