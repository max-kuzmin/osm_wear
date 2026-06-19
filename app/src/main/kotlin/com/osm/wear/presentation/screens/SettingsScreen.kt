package com.osm.wear.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.MapTheme

@Composable
fun SettingsScreen(
    vm: MapViewModel,
    onOpenRegions: () -> Unit,
    onOpenGpxFiles: () -> Unit,
    onBack: () -> Unit
) {
    val uiState         by vm.uiState.collectAsStateWithLifecycle()
    val downloadedRegions by vm.downloadedRegions.collectAsStateWithLifecycle()
    val gpxFiles        by vm.gpxFiles.collectAsStateWithLifecycle()

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
                        modifier = Modifier.size(24.dp)
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
                        modifier = Modifier.size(24.dp)
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
                onClick = {
                    val nextTheme = when (uiState.mapTheme) {
                        MapTheme.BIKER -> MapTheme.DARK
                        MapTheme.DARK -> MapTheme.DEFAULT
                        MapTheme.DEFAULT -> MapTheme.HILLSHADING
                        MapTheme.HILLSHADING -> MapTheme.INDIGO
                        MapTheme.INDIGO -> MapTheme.MOTORIDER
                        MapTheme.MOTORIDER -> MapTheme.OSMARENDER
                        MapTheme.OSMARENDER -> MapTheme.BIKER
                    }
                    vm.setMapTheme(nextTheme)
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
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("Map Theme", fontSize = 13.sp) },
                secondaryLabel = {
                    val themeText = when (uiState.mapTheme) {
                        MapTheme.BIKER -> "Biker"
                        MapTheme.DARK -> "Dark"
                        MapTheme.DEFAULT -> "Default"
                        MapTheme.HILLSHADING -> "Hillshading"
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
    }
}
