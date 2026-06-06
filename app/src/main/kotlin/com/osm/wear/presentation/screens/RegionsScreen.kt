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

@Composable
fun RegionsScreen(
    vm: MapViewModel,
    onOpenDownloadCatalog: () -> Unit,
    onRegionSelected: () -> Unit,
    onBack: () -> Unit
) {
    val downloadedRegions by vm.downloadedRegions.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                "Map Regions",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Download new region button ────────────────────────────────────────
        item {
            Button(
                onClick = onOpenDownloadCatalog,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("+ Download Region", fontSize = 13.sp)
            }
        }

        if (downloadedRegions.isEmpty()) {
            item {
                Text(
                    "No regions downloaded yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ── Downloaded regions list ───────────────────────────────────────────
        items(downloadedRegions.size) { idx ->
            val dr = downloadedRegions[idx]
            val isActive = dr.isActive

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
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    secondaryLabel = {
                        Text("${dr.fileSizeMb} MB${if (isActive) " ✓" else ""}", fontSize = 11.sp)
                    },
                    colors = if (isActive) ButtonDefaults.buttonColors(
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
