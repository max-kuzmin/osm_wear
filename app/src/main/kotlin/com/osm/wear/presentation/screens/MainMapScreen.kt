package com.osm.wear.presentation.screens

import android.content.Context
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.NavigationState
import com.osm.wear.domain.model.UserLocation
import com.osm.wear.presentation.map.layers.LocationArrowLayer
import kotlinx.coroutines.launch
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

@Composable
fun MainMapScreen(
    vm: MapViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val uiState  by vm.uiState.collectAsStateWithLifecycle()
    val location by vm.currentLocation.collectAsStateWithLifecycle()

    val navState = uiState.navigationState

    val focusRequester = remember { FocusRequester() }

    BackHandler { onOpenSettings() }

    // Request focus for rotary events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Stable references to Mapsforge layers
    val mapViewRef      = remember { mutableStateOf<MapView?>(null) }
    val locationMarker  = remember { mutableStateOf<LocationArrowLayer?>(null) }
    val gpxPolyline     = remember { mutableStateOf<Polyline?>(null) }
    val tileLayerRef    = remember { mutableStateOf<TileRendererLayer?>(null) }

    // ── Smooth Location Animation ──────────────────────────────────────────
    // We animate Lat/Long and Bearing to interpolate between GPS updates.
    val animLat = remember { Animatable(uiState.centerLat.toFloat()) }
    val animLon = remember { Animatable(uiState.centerLon.toFloat()) }
    val animBearing = remember { Animatable(0f) }

    // Update GPS dot and centering when location changes
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect

        // Target values
        val targetLat = loc.latitude.toFloat()
        val targetLon = loc.longitude.toFloat()
        var targetBearing = loc.bearing

        // Handle shortest path rotation for bearing (e.g. 350 -> 10 should go through 0)
        val currentBearing = animBearing.value
        val diff = ((targetBearing - currentBearing + 180) % 360 + 360) % 360 - 180
        targetBearing = currentBearing + diff

        // Kick off animations in parallel
        val duration = 800 // slightly less than 1s GPS interval
        
        // Animatable.animateTo is a suspend function.
        // LaunchedEffect provides a CoroutineScope.
        // We use launch to run them in parallel within this scope.
        launch { animLat.animateTo(targetLat, tween(duration)) }
        launch { animLon.animateTo(targetLon, tween(duration)) }
        launch { animBearing.animateTo(targetBearing, tween(duration)) }
    }

    // React to animated values
    val currentLat = animLat.value.toDouble()
    val currentLon = animLon.value.toDouble()
    val currentBearing = animBearing.value

    // Update marker position continuously
    SideEffect {
        locationMarker.value?.updatePosition(LatLong(currentLat, currentLon), currentBearing)
        if (uiState.followLocation) {
            mapViewRef.value?.model?.mapViewPosition?.setCenter(LatLong(currentLat, currentLon))
        }
    }

    // Update GPS dot when location changes (Handled by Animatable + SideEffect now)
    /*
    LaunchedEffect(location) {
        ...
    }
    */

    // Follow location logic (Handled by SideEffect for smooth panning)
    LaunchedEffect(uiState.followLocation, uiState.zoomLevel) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        mv.model.mapViewPosition.zoomLevel = uiState.zoomLevel.toByte()
        mv.postInvalidate()
    }

    // Update zoom when uiState.zoomLevel changes
    LaunchedEffect(uiState.zoomLevel) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        if (mv.model.mapViewPosition.zoomLevel.toInt() != uiState.zoomLevel) {
            mv.model.mapViewPosition.zoomLevel = uiState.zoomLevel.toByte()
        }
    }

    // Update GPX polyline when active GPX changes
    LaunchedEffect(uiState.activeGpxFile) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        val pts = uiState.activeGpxFile?.trackPoints?.map { LatLong(it.lat, it.lon) } ?: emptyList()
        updateGpxPolyline(mv, pts, gpxPolyline)
    }

    // Reload tile layer when active map file changes
    LaunchedEffect(uiState.activeMapFile) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        reloadTileLayer(context, mv, uiState.activeMapFile, tileLayerRef)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onRotaryScrollEvent {
                if (it.verticalScrollPixels > 0) vm.zoomOut() else vm.zoomIn()
                true
            }
    ) {

        // ── Mapsforge MapView ────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createMapView(ctx).also { mv ->
                    mapViewRef.value = mv

                    // Load initial tile layer
                    uiState.activeMapFile?.let { f ->
                        reloadTileLayer(ctx, mv, f, tileLayerRef)
                    }

                    // Draw initial GPX
                    uiState.activeGpxFile?.let { gpx ->
                        val pts = gpx.trackPoints.map { LatLong(it.lat, it.lon) }
                        updateGpxPolyline(mv, pts, gpxPolyline)
                    }

                    // Bezel rotation → zoom
                    mv.setOnGenericMotionListener { _, event ->
                        if (event.action == MotionEvent.ACTION_SCROLL) {
                            val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                            if (scroll > 0) vm.zoomIn() else vm.zoomOut()
                            true
                        } else false
                    }

                    // Detect panning to stop following
                    var startX = 0f
                    var startY = 0f
                    val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                    mv.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.x
                                startY = event.y
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = event.x - startX
                                val dy = event.y - startY
                                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                    vm.stopFollowingLocation()
                                }
                            }
                        }
                        false
                    }
                }
            },
            update = { mv ->
                // Ensure initial marker exists
                if (locationMarker.value == null && location != null) {
                    val loc = location!!
                    val newMarker = LocationArrowLayer(LatLong(loc.latitude, loc.longitude), loc.bearing, mv)
                    mv.layerManager.layers.add(newMarker)
                    locationMarker.value = newMarker
                }
                
                if (uiState.followLocation) {
                    mv.model.mapViewPosition.setCenter(LatLong(currentLat, currentLon))
                    mv.model.mapViewPosition.zoomLevel = uiState.zoomLevel.toByte()
                }
            }
        )

        // ── Map Controls (Curved Right side column) ─────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Zoom In (Top - slightly shifted left for curve)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = (-8).dp)
                    .background(Color(0x66000000), CircleShape)
                    .clickable { vm.zoomIn() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
            }
            // Zoom Out (Middle - stays at the edge)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0x66000000), CircleShape)
                    .clickable { vm.zoomOut() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
            }
            // Center on Location (Bottom - slightly shifted left for curve)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = (-8).dp)
                    .background(
                        if (uiState.followLocation) Color.White.copy(alpha = 0.6f)
                        else Color(0x661565C0),
                        CircleShape
                    )
                    .clickable { vm.centerOnLocation() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center on Location",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
            }
        }

        // ── Navigation overlay ────────────────────────────────────────────────
        if (navState != null && navState.isActive && navState.waypoints.isNotEmpty()) {
            NavigationOverlay(
                navState = navState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavigationOverlay(navState: NavigationState, modifier: Modifier = Modifier) {
    val distM = navState.distanceToNextTurnM
    val distText = if (distM >= 1000f) "%.1f km".format(distM / 1000f) else "${distM.toInt()} m"
    val arrow = bearingToArrow(navState.bearingToNextTurn)

    Row(
        modifier = modifier
            .background(
                color = Color(0xDD000000),
                shape = CircleShape
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(arrow, fontSize = 20.sp, color = Color.Yellow)
        Text(distText, fontSize = 13.sp, color = Color.White)
        if (navState.isOffTrack) {
            Text("⚠", fontSize = 13.sp, color = Color.Red)
        }
    }
}

private fun bearingToArrow(b: Float) = when {
    b < 22.5f  || b >= 337.5f -> "↑"
    b < 67.5f  -> "↗"
    b < 112.5f -> "→"
    b < 157.5f -> "↘"
    b < 202.5f -> "↓"
    b < 247.5f -> "↙"
    b < 292.5f -> "←"
    else       -> "↖"
}

// ─────────────────────────────────────────────────────────────────────────────
// Mapsforge helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun createMapView(context: Context): MapView {
    val mv = MapView(context)
    mv.isClickable = true
    mv.mapScaleBar.isVisible = true
    mv.setBuiltInZoomControls(false)
    mv.model.mapViewPosition.setCenter(LatLong(48.0, 16.0))
    mv.model.mapViewPosition.zoomLevel = 5
    return mv
}

private fun reloadTileLayer(
    context: Context,
    mv: MapView,
    mapFile: File?,
    ref: MutableState<TileRendererLayer?>
) {
    ref.value?.let { mv.layerManager.layers.remove(it); it.onDestroy() }
    ref.value = null
    if (mapFile == null || !mapFile.exists()) return
    val cache = AndroidUtil.createTileCache(
        context, "mapcache",
        mv.model.displayModel.tileSize,
        1f,
        mv.model.frameBufferModel.overdrawFactor
    )
    val layer = TileRendererLayer(
        cache,
        MapFile(mapFile),
        mv.model.mapViewPosition,
        AndroidGraphicFactory.INSTANCE
    ).apply { setXmlRenderTheme(MapsforgeThemes.OSMARENDER) }
    mv.layerManager.layers.add(0, layer)
    ref.value = layer
}

private fun updateGpxPolyline(
    mv: MapView,
    points: List<LatLong>,
    ref: MutableState<Polyline?>
) {
    ref.value?.let { mv.layerManager.layers.remove(it) }
    ref.value = null
    if (points.isEmpty()) return
    val paint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(android.graphics.Color.argb(200, 255, 80, 0))
        strokeWidth = 6f
        setStyle(Style.STROKE)
    }
    val poly = Polyline(paint, AndroidGraphicFactory.INSTANCE)
    poly.latLongs.addAll(points)
    mv.layerManager.layers.add(poly)
    ref.value = poly
}
