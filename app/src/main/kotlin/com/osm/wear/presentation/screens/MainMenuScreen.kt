package com.osm.wear.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.presentation.components.NavigationButton
import com.osm.wear.presentation.theme.AppDimensions
import com.osm.wear.view_models.MainMenuEffect
import com.osm.wear.view_models.MainMenuIntent
import com.osm.wear.view_models.MainMenuViewModel

@Composable
fun MainMenuScreen(
    menuVm: MainMenuViewModel,
    onNavigationStarted: () -> Unit,
    onOpenGpxTracks: () -> Unit,
    onOpenMarkers: () -> Unit,
    onOpenRegions: () -> Unit,
    onOpenPreferences: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by menuVm.uiState.collectAsStateWithLifecycle()
    val activeGpxFile = uiState.activeGpxFile
    val navState = uiState.navigationState
    
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        menuVm.effect.collect { effect ->
            when (effect) {
                is MainMenuEffect.ShowMap -> {
                    onNavigationStarted()
                }
                is MainMenuEffect.ShowToast -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
                text = "Menu",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // 1. Start/stop navigation (active if activeGpxFile != null)
        item {
            val isActive = navState?.isActive == true
            val hasTrack = activeGpxFile != null
            NavigationButton(
                isActive = isActive,
                enabled = hasTrack,
                onStart = {
                    activeGpxFile?.let { menuVm.onIntent(MainMenuIntent.StartNavigation(it, null)) }
                },
                onStop = {
                    menuVm.onIntent(MainMenuIntent.StopNavigation)
                }
            )
        }

        // 2. GPX Tracks
        item {
            Button(
                onClick = onOpenGpxTracks,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = "GPX Tracks",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("GPX Tracks", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    Text(
                        text = activeGpxFile?.name ?: "None selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        // 3. Markers
        item {
            Button(
                onClick = onOpenMarkers,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Markers",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Markers", style = MaterialTheme.typography.labelMedium) }
            )
        }

        // 4. Regions
        item {
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
                        contentDescription = "Regions",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Map Regions", style = MaterialTheme.typography.labelMedium) },
                secondaryLabel = {
                    Text(
                        text = uiState.activeRegion?.name ?: "None selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            )
        }

        // 4. Preferences
        item {
            Button(
                onClick = onOpenPreferences,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Preferences",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Preferences", style = MaterialTheme.typography.labelMedium) }
            )
        }

        item {
            BackButton(onClick = onBack)
        }
    }
}
