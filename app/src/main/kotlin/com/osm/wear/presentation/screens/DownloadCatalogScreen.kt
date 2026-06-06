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
import com.osm.wear.data.map.MapRegionCatalog
import com.osm.wear.domain.model.DownloadState

@Composable
fun DownloadCatalogScreen(
    vm: MapViewModel,
    onDownloadComplete: () -> Unit,
    onBack: () -> Unit
) {
    val downloadedRegions by vm.downloadedRegions.collectAsStateWithLifecycle()
    val downloadState     by vm.downloadState.collectAsStateWithLifecycle()

    // Navigate back to RegionsScreen when a download finishes
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(downloadState) {
        when (downloadState) {
            is DownloadState.Downloading -> wasDownloading = true
            is DownloadState.Idle -> if (wasDownloading) {
                wasDownloading = false
                onDownloadComplete()
            }
            else -> {}
        }
    }

    val alreadyDownloaded = downloadedRegions.map { it.region.id }.toSet()
    val grouped = MapRegionCatalog.all.groupBy { it.continent }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                "Download Region",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Active download progress ──────────────────────────────────────────
        when (val ds = downloadState) {
            is DownloadState.Downloading -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Downloading ${ds.region.name}…",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ds.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${ds.progressPercent}%  (${ds.downloadedMb.toInt()} MB)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is DownloadState.Failed -> {
                item {
                    Text(
                        "Failed: ${ds.error}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {}
        }

        // ── Region list grouped by continent ─────────────────────────────────
        grouped.forEach { (continent, regions) ->
            item {
                Text(
                    continent,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(regions.size) { idx ->
                val region = regions[idx]
                val isDownloaded  = region.id in alreadyDownloaded
                val isDownloading = downloadState is DownloadState.Downloading &&
                        (downloadState as DownloadState.Downloading).region.id == region.id
                val isBusy = downloadState is DownloadState.Downloading

                Button(
                    onClick = {
                        if (!isDownloaded && !isBusy) vm.downloadRegion(region)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloaded && !isBusy,
                    label = {
                        Text(
                            region.name,
                            fontSize = 13.sp,
                            color = if (isDownloaded) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text(
                            when {
                                isDownloaded  -> "Downloaded"
                                isDownloading -> "Downloading…"
                                else          -> "~${region.fileSizeMb} MB"
                            },
                            fontSize = 11.sp
                        )
                    },
                    colors = if (isDownloaded) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) else ButtonDefaults.buttonColors()
                )
            }
        }
    }
}
