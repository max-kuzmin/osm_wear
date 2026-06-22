package com.osm.wear.presentation.screens

import com.osm.wear.view_models.RegionsViewModel

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

import com.osm.wear.presentation.components.BackButton
import com.osm.wear.models.DownloadState

@Composable
fun RegionsScreen(
    regionsVm: RegionsViewModel,
    onRegionSelected: () -> Unit,
    onBack: () -> Unit
) {
    val downloadedRegions by regionsVm.downloadedRegions.collectAsStateWithLifecycle()
    val downloadState     by regionsVm.downloadState.collectAsStateWithLifecycle()
    val groupedRegions    by regionsVm.groupedRegions.collectAsStateWithLifecycle()

    val alreadyDownloadedIds = downloadedRegions.map { it.region.id }.toSet()

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Map Regions",
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

        // ── Downloaded Regions ──────────────────────────────────────────
        if (downloadedRegions.isNotEmpty()) {
            item {
                Text(
                    "Downloaded",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            item {
                Text(
                    "No regions downloaded yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        items(downloadedRegions.size) { idx ->
            val dr = downloadedRegions[idx]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        regionsVm.setActiveRegion(dr.region)
                        onRegionSelected()
                    },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            dr.region.name,
                            fontSize = 13.sp
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "${dr.fileSizeMb} MB${if (dr.isActive) " ✓" else ""}",
                            fontSize = 11.sp,
                            color = if (dr.isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    },
                    colors = if (dr.isActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.width(4.dp))
                com.osm.wear.presentation.components.RemoveButton(
                    onClick = { regionsVm.deleteRegion(dr.region) }
                )
            }
        }

        // ── Available Regions (Grouped) ──────────────────────────────────
        groupedRegions.forEach { (continent, regions) ->
            val availableRegions = regions.filter { it.id !in alreadyDownloadedIds }
            
            if (availableRegions.isNotEmpty()) {
                item {
                    Text(
                        continent,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                items(availableRegions.size) { idx ->
                    val region = availableRegions[idx]
                    val isDownloading = downloadState is DownloadState.Downloading &&
                            (downloadState as DownloadState.Downloading).region.id == region.id
                    val isBusy = downloadState is DownloadState.Downloading

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { if (!isBusy) regionsVm.downloadRegion(region) },
                            modifier = Modifier.weight(1f),
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            label = {
                                Text(
                                    region.name,
                                    fontSize = 13.sp
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
                                        text = "~${region.fileSizeMb} MB",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        )
                        
                        if (isDownloading) {
                            com.osm.wear.presentation.components.RemoveButton(
                                onClick = { regionsVm.cancelDownload() }
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(Modifier.height(8.dp))
        }

        item {
            BackButton(onClick = onBack)
        }
    }
}


