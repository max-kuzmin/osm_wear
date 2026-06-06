package com.osm.wear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

@Composable
fun SettingsScreen(
    vm: MapViewModel,
    onOpenRegions: () -> Unit,
    onOpenGpxFiles: () -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val uiState         by vm.uiState.collectAsStateWithLifecycle()
    val downloadedRegions by vm.downloadedRegions.collectAsStateWithLifecycle()
    val gpxFiles        by vm.gpxFiles.collectAsStateWithLifecycle()
    val navState        = uiState.navigationState

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Settings",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Map Regions ───────────────────────────────────────────────────────
        item {
            Button(
                onClick = onOpenRegions,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Map Regions") },
                secondaryLabel = {
                    val active = downloadedRegions.firstOrNull { it.isActive }
                    Text(active?.region?.name ?: "None selected")
                },
                colors = ButtonDefaults.buttonColors()
            )
        }

        // ── GPX Files ─────────────────────────────────────────────────────────
        item {
            Button(
                onClick = onOpenGpxFiles,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GPX Files") },
                secondaryLabel = {
                    val active = gpxFiles.firstOrNull { it.isActive }
                    Text(active?.name ?: "None selected")
                },
                colors = ButtonDefaults.buttonColors()
            )
        }

        // ── Navigation button ─────────────────────────────────────────────────
        item {
            if (navState != null && navState.isActive) {
                Button(
                    onClick = onStopNavigation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Navigation", fontSize = 13.sp)
                }
            } else {
                val hasActiveGpx = gpxFiles.any { it.isActive }
                Button(
                    onClick = onStartNavigation,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasActiveGpx,
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Start Navigation", fontSize = 13.sp)
                }
                if (!hasActiveGpx) {
                    Text(
                        "Select a GPX file first",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
