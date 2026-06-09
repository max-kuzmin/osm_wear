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

@Composable
fun RegionsScreen(
    vm: MapViewModel,
    onOpenDownloadCatalog: () -> Unit,
    onRegionSelected: () -> Unit,
    onBack: () -> Unit
) {
    val downloadedRegions by vm.downloadedRegions.collectAsStateWithLifecycle()

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

        item {
            Button(
                onClick = onOpenDownloadCatalog,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("+ Download Region", fontSize = 13.sp) }
            )
        }

        if (downloadedRegions.isEmpty()) {
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
                        vm.setActiveRegion(dr.region)
                        onRegionSelected()
                    },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            dr.region.name,
                            fontSize = 13.sp,
                            color = if (dr.isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text(
                            "${dr.fileSizeMb} MB${if (dr.isActive) " ✓" else ""}",
                            fontSize = 11.sp
                        )
                    },
                    colors = if (dr.isActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else ButtonDefaults.buttonColors()
                )
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { vm.deleteRegion(dr.region) },
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
