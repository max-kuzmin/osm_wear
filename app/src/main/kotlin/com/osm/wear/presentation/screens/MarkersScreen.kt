package com.osm.wear.presentation.screens

import com.osm.wear.view_models.*

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Route
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.presentation.components.RemoveButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun MarkersScreen(
    navVm: NavigationViewModel,
    markerVm: MarkerViewModel,
    mapVm: MapViewModel,
    settingsVm: SettingsViewModel,
    gpxVm: GpxFilesViewModel,
    onOpenSearch: () -> Unit,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val markerState by markerVm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    val tappedPoint = markerState.tappedPoint

    BackHandler { onBack() }

    val bookmarks by markerVm.bookmarks.collectAsStateWithLifecycle()

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
                text = "Markers",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // ── Build Route (Start navigation to target) ────────────────────────
        item {
            Button(
                onClick = {
                    if (tappedPoint != null) {
                        navVm.startNavigationToPoint(
                            tappedPoint,
                            markerVm.currentLocation.value,
                            settingsState.navigationMode,
                            onGpxCreated = { gpx ->
                                gpxVm.setActiveGpxFile(gpx)
                                val error = navVm.startNavigation(gpx, markerVm.currentLocation.value) { newMode ->
                                    settingsVm.setGpsBatteryMode(newMode)
                                }
                                if (error != null) {
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                } else {
                                    mapVm.centerOnLocation()
                                    onStartNavigation() // pop back to Map screen
                                }
                            },
                            onFailure = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = tappedPoint != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = "Build Route",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Build route", style = MaterialTheme.typography.labelMedium) }
            )
        }

        // ── Save from map / Search Address Buttons ─────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.ListSpacingTight)
            ) {
                Button(
                    onClick = {
                        if (tappedPoint != null) {
                            markerVm.saveBookmarkFromMap(tappedPoint, markerState.tappedPointName, markerState.tappedPointAddress)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = tappedPoint != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Save from Map",
                            modifier = Modifier.size(AppDimensions.IconNormal)
                        )
                    },
                    label = { Text("Save from map", style = MaterialTheme.typography.labelSmall) }
                )

                Button(
                    onClick = {
                        onOpenSearch()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Address",
                            modifier = Modifier.size(AppDimensions.IconNormal)
                        )
                    },
                    label = { Text("Search address", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // ── Coordinates Info / Status ──────────────────────────────────
        if (tappedPoint == null) {
            item {
                Text(
                    text = "Tap map to set target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(
                        top = AppDimensions.PaddingLabelTop,
                        start = AppDimensions.ScreenHorizontalPadding,
                        end = AppDimensions.ScreenHorizontalPadding
                    )
                )
            }
        }

        // ── Bookmarks Section ─────────────────────────────────────────
        if (bookmarks.isNotEmpty()) {
            items(bookmarks.size) { idx ->
                val bookmark = bookmarks[idx]
                val isSelected = tappedPoint != null && tappedPoint.lat == bookmark.lat && tappedPoint.lon == bookmark.lon

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.ListSpacingTight)
                ) {
                    Button(
                        onClick = {
                            markerVm.selectBookmark(bookmark)
                            mapVm.centerOnPoint(bookmark.lat, bookmark.lon)
                            onStartNavigation() // navigate to Map screen
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (isSelected) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        label = {
                            Text(
                                text = bookmark.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = {
                            Column(
                                horizontalAlignment = Alignment.Start
                            ) {
                                val line2Text = if (!bookmark.address.isNullOrBlank()) {
                                    var cleanAddr = bookmark.address
                                    val name = bookmark.name.trim()
                                    if (cleanAddr.startsWith(name, ignoreCase = true)) {
                                        cleanAddr = cleanAddr.substring(name.length).trim().removePrefix(",").trim()
                                    }
                                    if (cleanAddr.isBlank()) null else cleanAddr
                                } else {
                                    null
                                }
                                if (line2Text != null) {
                                    Text(
                                        text = line2Text,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "%.4f, %.4f".format(bookmark.lat, bookmark.lon),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )

                    RemoveButton(
                        onClick = {
                            markerVm.deleteBookmark(bookmark)
                        }
                    )
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
