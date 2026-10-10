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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.osm.wear.presentation.components.BackButton
import com.osm.wear.presentation.theme.AppDimensions
import com.osm.wear.view_models.TracksEffect
import com.osm.wear.view_models.TracksIntent
import com.osm.wear.view_models.TracksViewModel

@Composable
fun TracksScreen(
    tracksVm: TracksViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by tracksVm.uiState.collectAsStateWithLifecycle()

    val routes = uiState.routes
    val activeRoute = uiState.activeRoute
    val isSaving = uiState.isSaving

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
            tracksVm.onIntent(TracksIntent.ScanFolders)
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
                android.util.Log.e("TracksScreen", "Failed to launch ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    android.util.Log.e("TracksScreen", "Failed to launch ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION", e2)
                    try {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } catch (e3: Exception) {
                        android.util.Log.e("TracksScreen", "Failed to launch READ_EXTERNAL_STORAGE request", e3)
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
                tracksVm.onIntent(TracksIntent.ScanFolders)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        tracksVm.effect.collect { effect ->
            when (effect) {
                is TracksEffect.ShowToast -> {
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
                text = "Tracks",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = AppDimensions.PaddingTitleBottom)
            )
        }

        // ── Save Current Button ──────────────────────────────────────────
        item {
            val canSave = activeRoute != null && activeRoute.route != null && !isSaving
            Button(
                onClick = {
                    activeRoute?.route?.let { route ->
                        val nameToSave = if (activeRoute.name.isBlank() || activeRoute.name == "path_finder") {
                            "Saved Route"
                        } else {
                            activeRoute.name
                        }
                        tracksVm.onIntent(TracksIntent.SaveCurrent(nameToSave, route))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
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

        if (routes.isEmpty()) {
            item {
                Text(
                    text = "No tracks found on device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimensions.PaddingHeaderTop)
                )
            }
            item {
                Text(
                    text = "To add tracks, copy them to the watch directory:\n/Download/",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(
                        start = AppDimensions.TrackFilesAlertPaddingHorizontal,
                        end = AppDimensions.TrackFilesAlertPaddingHorizontal,
                        top = AppDimensions.TrackFilesAlertPaddingTop
                    )
                )
            }
        }

        items(routes.size) { idx ->
            val route = routes[idx]
            Button(
                onClick = {
                    tracksVm.onIntent(TracksIntent.SetActive(route.id))
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                secondaryLabel = {
                    Text(
                        text = "%.1f km".format(route.totalDistanceKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (route.isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                },
                colors = if (route.isActive) ButtonDefaults.buttonColors(
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
