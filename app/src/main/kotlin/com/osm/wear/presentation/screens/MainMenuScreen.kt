package com.osm.wear.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.models.GpxFile
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.presentation.components.NavigationButton
import com.osm.wear.presentation.theme.AppDimensions
import com.osm.wear.view_models.MainMenuViewModel

@Composable
fun MainMenuScreen(
    menuVm: MainMenuViewModel,
    onStartNavigation: (GpxFile) -> Unit,
    onStopNavigation: () -> Unit,
    onOpenGpxTracks: () -> Unit,
    onOpenMarkers: () -> Unit,
    onOpenPreferences: () -> Unit,
    onBack: () -> Unit
) {
    val activeGpxFile by menuVm.activeGpxFile.collectAsStateWithLifecycle()
    val navState by menuVm.navigationState.collectAsStateWithLifecycle()

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
                    activeGpxFile?.let { onStartNavigation(it) }
                },
                onStop = onStopNavigation
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
