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
import com.osm.wear.models.Bookmark

@Composable
fun MarkersScreen(
    markersVm: MarkersViewModel,
    onOpenSearch: () -> Unit,
    onNavigateToMap: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tappedPoint by markersVm.currentMarker.collectAsStateWithLifecycle()
    val bookmarkDistances by markersVm.bookmarkDistances.collectAsStateWithLifecycle()

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
                        markersVm.buildRouteToPoint(
                            tappedPoint!!,
                            onRouteBuilt = {
                                onNavigateToMap() // pop back to Map screen
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
                            markersVm.saveBookmarkFromMap(tappedPoint!!)
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
        if (bookmarkDistances.isNotEmpty()) {
            items(bookmarkDistances.size) { idx ->
                val (bookmark, distance) = bookmarkDistances[idx]
                val isSelected = tappedPoint != null && tappedPoint!!.lat == bookmark.lat && tappedPoint!!.lon == bookmark.lon

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.ListSpacingTight)
                ) {
                    Button(
                        onClick = {
                            markersVm.selectBookmark(bookmark)
                            onNavigateToMap() // navigate to Map screen
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
                                val distanceText = distance?.let {
                                    if (it >= 1000) "%.1f km".format(it / 1000.0) else "${it.toInt()} m"
                                }
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
                                
                                val subText = when {
                                    distanceText != null && line2Text != null -> "$distanceText • $line2Text"
                                    distanceText != null -> distanceText
                                    line2Text != null -> line2Text
                                    else -> "%.4f, %.4f".format(bookmark.lat, bookmark.lon)
                                }
                                
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )

                    RemoveButton(
                        onClick = {
                            markersVm.deleteBookmark(bookmark)
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
