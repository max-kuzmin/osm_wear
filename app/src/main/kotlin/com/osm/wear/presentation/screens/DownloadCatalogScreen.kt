package com.osm.wear.presentation.screens

import androidx.activity.compose.BackHandler
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

import com.osm.wear.domain.model.DownloadState

@Composable
fun DownloadCatalogScreen(
    vm: MapViewModel,
    onDownloadComplete: () -> Unit,
    onBack: () -> Unit
) {
    val downloadedRegions by vm.downloadedRegions.collectAsStateWithLifecycle()
    val downloadState     by vm.downloadState.collectAsStateWithLifecycle()
    val groupedRegions    by vm.groupedRegions.collectAsStateWithLifecycle()

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

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Download Region",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── Download error ──────────────────────────────────────────
        if (downloadState is DownloadState.Failed) {
            val ds = downloadState as DownloadState.Failed
            item {
                Text(
                    "Failed: ${ds.error}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Region list grouped by continent ─────────────────────────────────
        groupedRegions.forEach { (continent, regions) ->
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { if (!isDownloaded && !isBusy) vm.downloadRegion(region) },
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloaded && !isBusy,
                        label = {
                            Text(
                                region.name,
                                fontSize = 13.sp,
                                color = if (isDownloaded) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        secondaryLabel = {
                            if (isDownloading) {
                                val ds = downloadState as DownloadState.Downloading
                                Text(
                                    text = "${ds.progressPercent}% (${ds.downloadedMb.toInt()} MB)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = if (isDownloaded) "Downloaded" else "~${region.fileSizeMb} MB",
                                    fontSize = 11.sp
                                )
                            }
                        },
                        colors = if (isDownloaded) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.buttonColors()
                    )
                    
                    if (isDownloading) {
                        Button(
                            onClick = { vm.cancelDownload() },
                            modifier = Modifier.size(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("✕", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}


