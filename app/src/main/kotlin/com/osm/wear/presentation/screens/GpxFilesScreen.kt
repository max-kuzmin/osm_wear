package com.osm.wear.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop

@Composable
fun GpxFilesScreen(
    vm: MapViewModel,
    onGpxSelected: () -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val gpxFiles by vm.gpxFiles.collectAsStateWithLifecycle()
    val navState = uiState.navigationState

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.importGpxFile(it) }
    }

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "GPX Files",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("Open GPX File", fontSize = 13.sp) }
            )
        }

        // ── Navigation Control ──────────────────────────────────────────
        item {
            NavigationButton(
                isActive = navState?.isActive == true,
                hasActiveGpx = gpxFiles.any { it.isActive },
                onStart = onStartNavigation,
                onStop = onStopNavigation
            )
        }

        if (gpxFiles.isEmpty()) {
            item {
                Text(
                    "No GPX files added yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        items(gpxFiles.size) { idx ->
            val gpx = gpxFiles[idx]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        vm.setActiveGpxFile(gpx)
                        onGpxSelected()
                    },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            gpx.name,
                            fontSize = 13.sp,
                            color = if (gpx.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text(
                            "%.1f km · ${gpx.trackPoints.size} pts${if (gpx.isActive) " ✓" else ""}"
                                .format(gpx.totalDistanceKm),
                            fontSize = 11.sp,
                            color = if (gpx.isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = if (gpx.isActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) else ButtonDefaults.buttonColors()
                )
                Spacer(Modifier.width(4.dp))
                com.osm.wear.presentation.components.RemoveButton(
                    onClick = { vm.deleteGpxFile(gpx.id) }
                )
            }
        }
    }
}

@Composable
private fun NavigationButton(
    isActive: Boolean,
    hasActiveGpx: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isActive) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            ) {
                Text("Stop Navigation", fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasActiveGpx,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("Start Navigation", fontSize = 13.sp) }
            )
        }
    }
}
