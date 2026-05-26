package com.osm.wear.presentation.screens

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
import com.osm.wear.data.map.MapRegionCatalog
import com.osm.wear.domain.model.MapRegion
import com.osm.wear.domain.model.RegionStatus

@Composable
fun DownloadScreen(viewModel: MapViewModel) {
    val downloadedIds by viewModel.downloadedRegionIds.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val continents = MapRegionCatalog.continents

    var selectedContinent by remember { mutableStateOf<String?>(null) }

    if (selectedContinent == null) {
        // Show continent list
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "Download Maps",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(continents) { continent ->
                val regionCount = MapRegionCatalog.byContinent(continent).size
                val downloadedCount = MapRegionCatalog.byContinent(continent)
                    .count { it.id in downloadedIds }

                Chip(
                    onClick = { selectedContinent = continent },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(continent, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        Text(
                            "$downloadedCount/$regionCount downloaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (downloadedCount > 0) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                )
            }
        }
    } else {
        // Show regions for selected continent
        val regions = MapRegionCatalog.byContinent(selectedContinent!!)

        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactButton(
                        onClick = { selectedContinent = null },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("‹", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = selectedContinent!!,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            items(regions) { region ->
                val isDownloaded = region.id in downloadedIds
                val progress = activeDownloads[region.id]
                val isDownloading = progress != null

                RegionItem(
                    region = region,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadPercent = progress?.percent ?: 0,
                    onDownload = { viewModel.downloadRegion(region) },
                    onDelete = { viewModel.deleteRegion(region) },
                    onSelect = { viewModel.selectRegionAsActiveMap(region) }
                )
            }
        }
    }
}

@Composable
private fun RegionItem(
    region: MapRegion,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadPercent: Int,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val sizeStr = MapRegionCatalog.formatSize(region.fileSizeBytes)

    Card(
        onClick = if (isDownloaded) onSelect else if (!isDownloading) onDownload else { {} },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                when {
                    isDownloading -> Text(
                        "$downloadPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2196F3)
                    )
                    isDownloaded -> CompactButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B0000)
                        )
                    ) {
                        Text("✕", style = MaterialTheme.typography.labelSmall)
                    }
                    else -> Text(
                        sizeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            if (isDownloading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { downloadPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isDownloaded) {
                Text(
                    text = "✓ Tap to use",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}
