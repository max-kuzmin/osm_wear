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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("Map Regions", fontSize = 13.sp) },
                secondaryLabel = { Text(activeRegion?.region?.name ?: "None selected", fontSize = 11.sp) }
            )
        }

        item {
            val activeGpx = gpxFiles.firstOrNull { it.isActive }
            Button(
                onClick = onOpenGpxFiles,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("GPX Files", fontSize = 13.sp) },
                secondaryLabel = { Text(activeGpx?.name ?: "None selected", fontSize = 11.sp) }
            )
        }
    }
}
