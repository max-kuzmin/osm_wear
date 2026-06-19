package com.osm.wear.presentation.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop

@Composable
fun GpxFilesScreen(
    vm: MapViewModel,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val gpxFiles by vm.gpxFiles.collectAsStateWithLifecycle()
    val navState = uiState.navigationState

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
            vm.scanGpxFolders()
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
                vm.scanGpxFolders()
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
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "GPX Files",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── Navigation Control ──────────────────────────────────────────
        item {
            NavigationButton(
                isActive = navState?.isActive == true,
                hasActiveGpx = gpxFiles.any { it.isActive },
                onStart = onStartNavigation,
                onStop = onStopNavigation
            )
        }

        if (gpxFiles.isEmpty()) {
            item {
                Text(
                    "No GPX files found on device",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Text(
                    "To add GPX files, copy them to the watch directory:\n/Download/",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp)
                )
            }
        }

        items(gpxFiles.size) { idx ->
            val gpx = gpxFiles[idx]
            Button(
                onClick = {
                    vm.setActiveGpxFile(gpx)
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        gpx.name,
                        fontSize = 13.sp
                    )
                },
                secondaryLabel = {
                    Text(
                        "%.1f km · ${gpx.trackPoints.size} pts${if (gpx.isActive) " ✓" else ""}"
                            .format(gpx.totalDistanceKm),
                        fontSize = 11.sp
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
    }
}

@Composable
private fun NavigationButton(
    isActive: Boolean,
    hasActiveGpx: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isActive) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            ) {
                Text("Stop Navigation", fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasActiveGpx,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("Start Navigation", fontSize = 13.sp) }
            )
        }
    }
}
