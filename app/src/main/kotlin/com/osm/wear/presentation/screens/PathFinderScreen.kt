package com.osm.wear.presentation.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.NavigationMode

@Composable
fun PathFinderScreen(
    vm: MapViewModel,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val navState = uiState.navigationState
    val tappedPoint = uiState.tappedPoint

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Path Finder",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── Start / Stop Navigation ────────────────────────────────────
        item {
            val isNavActive = navState?.isActive == true && navState.gpxFile?.id == "path_finder"
            
            if (isNavActive) {
                Button(
                    onClick = { vm.stopNavigation() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Navigation",
                            modifier = Modifier.size(24.dp),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    },
                    label = { Text("Stop Navigation", fontSize = 13.sp) }
                )
            } else {
                Button(
                    onClick = {
                        vm.startNavigationToPoint { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
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
                            modifier = Modifier.size(24.dp),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    },
                    label = { Text("Start Navigation", fontSize = 13.sp) }
                )
            }
        }

        // ── Navigation selection mode (Walking, Cycling, Driving) ──────
        item {
            val modeIcon = when (uiState.navigationMode) {
                NavigationMode.WALKING -> Icons.Default.DirectionsWalk
                NavigationMode.CYCLING -> Icons.Default.DirectionsBike
                NavigationMode.DRIVING -> Icons.Default.DirectionsCar
            }
            
            Button(
                onClick = { vm.cycleNavigationMode() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                },
                label = { Text("Travel Mode", fontSize = 13.sp) },
                secondaryLabel = {
                    Text(
                        text = uiState.navigationMode.label,
                        fontSize = 11.sp,
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
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}
