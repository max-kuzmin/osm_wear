package com.osm.wear.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun GpxFilesScreen(
    vm: MapViewModel,
    onGpxSelected: () -> Unit,
    onBack: () -> Unit
) {
    val gpxFiles by vm.gpxFiles.collectAsStateWithLifecycle()

    // File picker — opens system file manager to pick a .gpx file
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.importGpxFile(it) }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                "GPX Files",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Open GPX button ───────────────────────────────────────────────────
        item {
            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("+ Open GPX File", fontSize = 13.sp)
            }
        }

        if (gpxFiles.isEmpty()) {
            item {
                Text(
                    "No GPX files added yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ── GPX file list ─────────────────────────────────────────────────────
        items(gpxFiles.size) { idx ->
            val gpx = gpxFiles[idx]
            val isActive = gpx.isActive

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
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text(
                            "%.1f km · ${gpx.trackPoints.size} pts${if (isActive) " ✓" else ""}".format(gpx.totalDistanceKm),
                            fontSize = 11.sp
                        )
                    },
                    colors = if (isActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else ButtonDefaults.buttonColors()
                )
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { vm.deleteGpxFile(gpx.id) },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text("✕", fontSize = 11.sp)
                }
            }
        }
    }
}
