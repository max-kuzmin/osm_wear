package com.osm.wear.presentation.screens

import com.osm.wear.view_models.*

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Save
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

import androidx.core.content.ContextCompat
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.presentation.components.NavigationButton
import com.osm.wear.models.GpxFile
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun GpxTracksScreen(
    gpxVm: GpxTracksViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gpxFiles by gpxVm.gpxFiles.collectAsStateWithLifecycle()

    // Check if the active GPX track is covered by the downloaded map
    val activeGpx = gpxFiles.find { it.isActive }
    val isCovered by gpxVm.isActiveGpxCovered.collectAsStateWithLifecycle()

    // Evaluate warning and start conditions dynamically
    val mapWarning = remember(activeGpx, isCovered) {
        when {
            activeGpx == null -> null
            !isCovered -> "Track outside map area or map not downloaded"
            else -> null
        }
    }

    val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            gpxVm.scanGpxFolders()
        }
    }

    val requestPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("GpxTracksScreen", "Failed to launch ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    android.util.Log.e("GpxTracksScreen", "Failed to launch ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION", e2)
                    try {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } catch (e3: Exception) {
                        android.util.Log.e("GpxTracksScreen", "Failed to launch READ_EXTERNAL_STORAGE request", e3)
                    }
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAllFilesAccess) {
            requestPermission()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                gpxVm.scanGpxFolders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                text = "GPX Tracks",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // ── Save Current Button ──────────────────────────────────────────
        item {
            Button(
                onClick = {
                    activeGpx?.let { gpx ->
                        val nameToSave = if (gpx.id == "path_finder") "Saved Route" else gpx.name
                        gpxVm.saveCurrentGpx(nameToSave, gpx.trackPoints) { success, error ->
                            if (success) {
                                android.widget.Toast.makeText(context, "Track saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Save failed: $error", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = activeGpx != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Save,
                        contentDescription = "Save current track",
                        modifier = Modifier.size(AppDimensions.IconNormal)
                    )
                },
                label = { Text("Save current", style = MaterialTheme.typography.labelMedium) }
            )
        }



        if (gpxFiles.isEmpty()) {
            item {
                Text(
                    "No GPX files found on device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimensions.PaddingHeaderTop)
                )
            }
            item {
                Text(
                    "To add GPX files, copy them to the watch directory:\n/Download/",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(
                        start = AppDimensions.GpxFilesAlertPaddingHorizontal,
                        end = AppDimensions.GpxFilesAlertPaddingHorizontal,
                        top = AppDimensions.GpxFilesAlertPaddingTop
                    )
                )
            }
        }

        items(gpxFiles.size) { idx ->
            val gpx = gpxFiles[idx]
            Button(
                onClick = {
                    gpxVm.setActiveGpxFile(gpx)
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        gpx.name,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                secondaryLabel = {
                    Text(
                        text = "%.1f km".format(gpx.totalDistanceKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gpx.isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                },
                colors = if (gpx.isActive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) else ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        item {
            Spacer(Modifier.height(AppDimensions.SpacerHeight))
        }

        item {
            BackButton(onClick = onBack)
        }
    }
}


