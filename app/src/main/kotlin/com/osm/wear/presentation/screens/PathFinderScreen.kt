package com.osm.wear.presentation.screens

import com.osm.wear.view_models.*

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import com.osm.wear.presentation.components.BackButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.models.NavigationMode
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun PathFinderScreen(
    navVm: NavigationViewModel,
    mapVm: MapViewModel,
    settingsVm: SettingsViewModel,
    gpxVm: GpxFilesViewModel,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by mapVm.uiState.collectAsStateWithLifecycle()
    val navState by navVm.navigationState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    val tappedPoint = uiState.tappedPoint

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            vertical = AppDimensions.ScreenVerticalPadding,
            horizontal = AppDimensions.ScreenHorizontalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.ListSpacingTight)
    ) {
        item {
            Text(
                text = "Path Finder",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // ── Start / Stop Navigation ────────────────────────────────────
        item {
            val currentNavState = navState
            val isNavActive = currentNavState?.isActive == true && currentNavState.gpxFile?.id == "path_finder"
            
            if (isNavActive) {
                Button(
                    onClick = { navVm.stopNavigation { /* Handle battery locally if needed */ } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Navigation",
                            modifier = Modifier.size(AppDimensions.IconNormal)
                        )
                    },
                    label = { Text("Stop Navigation", style = MaterialTheme.typography.labelMedium) }
                )
            } else {
                Button(
                    onClick = {
                        navVm.startNavigationToPoint(
                            tappedPoint!!,
                            mapVm.currentLocation.value,
                            uiState.centerLat,
                            uiState.centerLon,
                            settingsState.navigationMode,
                            onGpxCreated = { gpx ->
                                gpxVm.setActiveGpxFile(gpx)
                                navVm.startNavigation(gpx, mapVm.currentLocation.value) { }
                            },
                            onFailure = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                        onStartNavigation() // Navigate back to the map screen
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tappedPoint != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Navigation",
                            modifier = Modifier.size(AppDimensions.IconNormal)
                        )
                    },
                    label = { Text("Start Navigation", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // ── Navigation selection mode (Walking, Cycling, Driving) ──────
        item {
            val modeIcon = when (settingsState.navigationMode) {
                NavigationMode.WALKING -> Icons.Default.DirectionsWalk
                NavigationMode.CYCLING -> Icons.Default.DirectionsBike
                NavigationMode.DRIVING -> Icons.Default.DirectionsCar
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
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Travel Mode", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    Text(
                        text = settingsState.navigationMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        // ── Coordinates Info / Status ──────────────────────────────────
        item {
            val statusText = if (tappedPoint != null) {
                "Target: %.4f, %.4f".format(tappedPoint.lat, tappedPoint.lon)
            } else {
                "Tap map to set target"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    top = AppDimensions.PaddingLabelTop,
                    start = AppDimensions.ScreenHorizontalPadding,
                    end = AppDimensions.ScreenHorizontalPadding
                )
            )
        }

        item {
            Spacer(Modifier.height(AppDimensions.SpacerHeight))
        }

        item {
            BackButton(onClick = onBack)
        }
    }
}
