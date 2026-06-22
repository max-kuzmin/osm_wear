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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.models.MapTheme

@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel,
    mapVm: MapViewModel,
    regionsVm: RegionsViewModel,
    gpxVm: GpxFilesViewModel,
    onOpenRegions: () -> Unit,
    onOpenGpxFiles: () -> Unit,
    onOpenPathFinder: () -> Unit,
    onBack: () -> Unit
) {
    val uiState         by mapVm.uiState.collectAsStateWithLifecycle()
    val settingsState   by settingsVm.uiState.collectAsStateWithLifecycle()
    val downloadedRegions by regionsVm.downloadedRegions.collectAsStateWithLifecycle()
    val gpxFiles        by gpxVm.gpxFiles.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Settings",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
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
                        modifier = Modifier.size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                },
                label = { Text("Map Regions", fontSize = 13.sp) },
                secondaryLabel = { 
                    Text(
                        text = activeRegion?.region?.name ?: "None selected", 
                        fontSize = 11.sp,
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
                        modifier = Modifier.size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                },
                label = { Text("GPX Files", fontSize = 13.sp) },
                secondaryLabel = { 
                    Text(
                        text = activeGpx?.name ?: "None selected", 
                        fontSize = 11.sp,
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
                        modifier = Modifier.size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                },
                label = { Text("Path Finder", fontSize = 13.sp) },
                secondaryLabel = {
                    val tappedPoint = uiState.tappedPoint
                    val pointText = if (tappedPoint != null) {
                        "%.4f, %.4f".format(tappedPoint.lat, tappedPoint.lon)
                    } else {
                        "No point selected"
                    }
                    Text(
                        text = pointText,
                        fontSize = 11.sp,
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
                        modifier = Modifier.size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                },
                label = { Text("Map Theme", fontSize = 13.sp) },
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
                        fontSize = 11.sp,
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


