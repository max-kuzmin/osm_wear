package com.osm.wear.presentation.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.GpxTrack
import kotlin.math.roundToInt

@Composable
fun GpxScreen(viewModel: MapViewModel) {
    val tracks by viewModel.gpxTracks.collectAsStateWithLifecycle()

    // File picker launcher for GPX files
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importGpxFromUri(uri)
            }
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPX Tracks",
                    style = MaterialTheme.typography.titleSmall
                )
                CompactButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("application/gpx+xml", "application/xml", "text/xml", "text/plain")
                            )
                        }
                        launcher.launch(intent)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No GPX tracks.\nTap + to import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(tracks) { track ->
                GpxTrackItem(
                    track = track,
                    onToggleVisibility = { viewModel.toggleGpxTrackVisibility(track.id) },
                    onDelete = { viewModel.deleteGpxTrack(track.id) }
                )
            }
        }
    }
}

@Composable
private fun GpxTrackItem(
    track: GpxTrack,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    val distanceStr = formatDistance(track.distanceMeters)

    Card(
        onClick = onToggleVisibility,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (track.isVisible) Color.White else Color.Gray
                )
                Text(
                    text = "${track.points.size} pts · $distanceStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.width(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Visibility toggle
                CompactButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (track.isVisible) Color(0xFF2196F3) else Color.DarkGray
                    )
                ) {
                    Text(
                        if (track.isVisible) "●" else "○",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Delete button
                CompactButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B0000)
                    )
                ) {
                    Text("✕", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String = when {
    meters >= 1000 -> "${"%.1f".format(meters / 1000)} km"
    else -> "${meters.roundToInt()} m"
}
