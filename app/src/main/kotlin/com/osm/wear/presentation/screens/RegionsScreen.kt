package com.osm.wear.presentation.screens

import com.osm.wear.view_models.RegionsViewModel
import com.osm.wear.view_models.RegionsIntent
import android.app.Activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

import com.osm.wear.presentation.components.BackButton
import com.osm.wear.models.DownloadState
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun RegionsScreen(
    regionsVm: RegionsViewModel,
    onRegionSelected: () -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by regionsVm.uiState.collectAsStateWithLifecycle()
    
    val downloadedRegions = uiState.downloadedRegions
    val downloadState = uiState.downloadState
    val groupedRegions = uiState.groupedRegions
    val validIds = uiState.validRegionIds
    val freeId = uiState.freeRegionId
    val isExpired = uiState.isFreeTrialExpired
    val isMonetizationEnabled = uiState.isMonetizationEnabled
    val prices = uiState.productPrices

    val alreadyDownloadedIds = downloadedRegions.map { it.region.id }.toSet()

    BackHandler { onBack() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            vertical = AppDimensions.ScreenVerticalPadding,
            horizontal = AppDimensions.ScreenHorizontalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.ListSpacingTight)
    ) {
        item {
            Text(
                text = "Map Regions",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // ── Download error ──────────────────────────────────────────
        if (downloadState is DownloadState.Failed) {
            val ds = downloadState as DownloadState.Failed
            item {
                Text(
                    "Failed: ${ds.error}",
                    style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = AppDimensions.PaddingHeaderTop)
                )
            }
        } else {
            item {
                Text(
                    "No regions downloaded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimensions.PaddingHeaderTop)
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
                        if (isMonetizationEnabled && !validIds.contains(dr.region.id)) {
                            regionsVm.launchBillingFlow(context as Activity, dr.region.id)
                        } else {
                            regionsVm.onIntent(RegionsIntent.SetActiveRegion(dr.region))
                            onRegionSelected()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            dr.region.name,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    secondaryLabel = {
                        val statusText = if (isMonetizationEnabled) {
                            if (validIds.contains(dr.region.id)) {
                                if (dr.region.id == freeId) "Free Trial (Active)" else "Active"
                            } else {
                                if (dr.region.id == freeId) "Free Trial (Expired)" else "Purchase Required"
                            }
                        } else {
                            if (dr.isActive) "Active ✓" else ""
                        }

                        val priceText = prices[dr.region.id.replace("/", "_")] ?: "$1.00"
                        val finalSecondary = if (isMonetizationEnabled && !validIds.contains(dr.region.id)) "Buy $priceText" else statusText

                        Text(
                            text = "${dr.fileSizeMb} MB | $finalSecondary",
                            style = MaterialTheme.typography.labelSmall,
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
                Spacer(Modifier.width(AppDimensions.SpacerWidth))
                com.osm.wear.presentation.components.RemoveButton(
                    onClick = { regionsVm.onIntent(RegionsIntent.DeleteRegion(dr.region)) }
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = AppDimensions.PaddingHeaderTop)
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
                         horizontalArrangement = Arrangement.spacedBy(AppDimensions.SpacerWidth)
                     ) {
                         val isFreeAvailable = isMonetizationEnabled && freeId == null && !isExpired
                         val isPurchasable = isMonetizationEnabled && !validIds.contains(region.id)
                         val priceText = prices[region.id.replace("/", "_")] ?: "$1.00"

                         Button(
                             onClick = { 
                                 if (!isBusy) {
                                     if (isPurchasable) {
                                         if (isFreeAvailable) {
                                             regionsVm.onIntent(RegionsIntent.ClaimFreeRegion(region))
                                         } else {
                                             regionsVm.launchBillingFlow(context as Activity, region.id)
                                         }
                                     } else {
                                         regionsVm.onIntent(RegionsIntent.DownloadRegion(region))
                                     }
                                 }
                             },
                             modifier = Modifier.weight(1f),
                             enabled = !isBusy,
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                 contentColor = MaterialTheme.colorScheme.onSurface
                             ),
                             label = {
                                 Text(
                                     region.name,
                                     style = MaterialTheme.typography.labelMedium
                                 )
                             },
                             secondaryLabel = {
                                 if (isDownloading) {
                                     val ds = downloadState as DownloadState.Downloading
                                     Text(
                                         text = "${ds.progressPercent}% (${ds.downloadedMb.toInt()} MB)",
                                         style = MaterialTheme.typography.labelSmall,
                                         color = MaterialTheme.colorScheme.primary
                                     )
                                 } else {
                                     val actionText = if (isPurchasable) {
                                         if (isFreeAvailable) "Get Free (7 Days)" else "Buy $priceText"
                                     } else {
                                         "Download"
                                     }
                                     Text(
                                         text = "~${region.fileSizeMb} MB | $actionText",
                                         style = MaterialTheme.typography.labelSmall,
                                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                     )
                                 }
                             }
                         )
                         
                         if (isDownloading) {
                             com.osm.wear.presentation.components.RemoveButton(
                                 onClick = { regionsVm.onIntent(RegionsIntent.CancelDownload) }
                             )
                         }
                     }
                }
            }
        }
        
        item {
            Spacer(Modifier.height(AppDimensions.SpacerHeight))
        }

        item {
            BackButton(onClick = onBack)
        }
    }
}
