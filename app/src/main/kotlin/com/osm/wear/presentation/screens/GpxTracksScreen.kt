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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

import androidx.core.content.ContextCompat
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.models.GpxFile
import com.osm.wear.presentation.theme.AppDimensions

@Composable
fun GpxTracksScreen(
    gpxVm: GpxTracksViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by gpxVm.uiState.collectAsStateWithLifecycle()
    
    val gpxFiles = uiState.gpxFiles
    val activeGpx = uiState.activeGpxFile
    val isCovered = uiState.isActiveGpxCovered
    val isSaving = uiState.isSaving

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
            gpxVm.onIntent(GpxIntent.ScanFolders)
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
                gpxVm.onIntent(GpxIntent.ScanFolders)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        gpxVm.effect.collect { effect ->
            when (effect) {
                is GpxEffect.ShowToast -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
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
                        gpxVm.onIntent(GpxIntent.SaveCurrent(nameToSave, gpx.trackPoints))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = activeGpx != null && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                icon = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppDimensions.IconNormal),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save current track",
                            modifier = Modifier.size(AppDimensions.IconNormal)
                        )
                    }
                },
                label = {
                    if (isSaving) {
                        Text("Saving...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("Save current", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
                    gpxVm.onIntent(GpxIntent.SetActive(gpx))
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
