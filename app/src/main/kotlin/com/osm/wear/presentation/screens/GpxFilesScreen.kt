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
fun GpxFilesScreen(
    gpxVm: GpxFilesViewModel,
    navVm: NavigationViewModel,
    settingsVm: SettingsViewModel,
    onStartNavigation: (GpxFile) -> Unit,
    onStopNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gpxFiles by gpxVm.gpxFiles.collectAsStateWithLifecycle()
    val navState by navVm.navigationState.collectAsStateWithLifecycle()

    // Check if the active GPX track is covered by the downloaded map
    val activeGpx = gpxFiles.find { it.isActive }
    val hasMap = navVm.hasActiveMapFile()
    val isCovered = activeGpx != null && hasMap && navVm.isGpxCoveredByMap(activeGpx)

    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    val isWalking = settingsState.navigationMode == NavigationMode.WALKING

    // Evaluate warning and start conditions dynamically
    val mapWarning = remember(activeGpx, hasMap, isCovered) {
        when {
            activeGpx == null -> null
            !hasMap -> "Download map first"
            !isCovered -> "Track outside map area"
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
                android.util.Log.e("GpxFilesScreen", "Failed to launch ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    android.util.Log.e("GpxFilesScreen", "Failed to launch ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION", e2)
                    try {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } catch (e3: Exception) {
                        android.util.Log.e("GpxFilesScreen", "Failed to launch READ_EXTERNAL_STORAGE request", e3)
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
                text = "GPX Files",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // ── Navigation Control ──────────────────────────────────────────
        item {
            NavigationButton(
                isActive = navState?.isActive == true,
                enabled = activeGpx != null,
                warningText = mapWarning,
                onStart = { activeGpx?.let { onStartNavigation(it) } },
                onStop = onStopNavigation
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


