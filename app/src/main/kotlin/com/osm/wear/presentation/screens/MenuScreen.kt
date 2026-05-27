package com.osm.wear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

@Composable
fun MenuScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToGpx: () -> Unit,
    onNavigateToRecord: () -> Unit,
    onNavigateToNavigation: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "OSM Wear",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        item {
            Chip(
                onClick = onNavigateToMap,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("🗺  Map") },
                secondaryLabel = { Text("View offline map") }
            )
        }

        item {
            Chip(
                onClick = onNavigateToDownload,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("⬇  Download") },
                secondaryLabel = { Text("Get offline regions") }
            )
        }

        item {
            Chip(
                onClick = onNavigateToGpx,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("📍  GPX Tracks") },
                secondaryLabel = { Text("Import & manage tracks") }
            )
        }

        item {
            Chip(
                onClick = onNavigateToRecord,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("⏺  Record Track") },
                secondaryLabel = { Text("Record GPS track") }
            )
        }

        item {
            Chip(
                onClick = onNavigateToNavigation,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("🧭  Navigate") },
                secondaryLabel = { Text("Turn-by-turn guidance") }
            )
        }
    }
}
