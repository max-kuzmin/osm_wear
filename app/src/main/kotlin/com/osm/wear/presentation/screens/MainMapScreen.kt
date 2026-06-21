package com.osm.wear.presentation.screens

import android.content.Context
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.osm.wear.presentation.map.layers.TrackMarkersLayer
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Rotation
import com.osm.wear.domain.model.MapRotationMode
import com.osm.wear.domain.model.MapTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
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

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 10 seconds of inactivity
    LaunchedEffect(lastInteractionTime) {
        controlsVisible = true
        kotlinx.coroutines.delay(10000L)
        controlsVisible = false
    }

    // Stable references to Mapsforge layers
    val mapViewRef      = remember { mutableStateOf<MapView?>(null) }
    val locationMarker  = remember { mutableStateOf<LocationArrowLayer?>(null) }
    val trackLayer      = remember { mutableStateOf<TrackMarkersLayer?>(null) }
    val tileLayerRef    = remember { mutableStateOf<TileRendererLayer?>(null) }
    val dotMarkLayer    = remember { mutableStateOf<com.osm.wear.presentation.map.layers.DotMarkLayer?>(null) }

    // ── Smooth Location Animation ──────────────────────────────────────────
    // We animate Lat/Long and Bearing to interpolate between GPS updates.
    val animLat = remember { Animatable(uiState.centerLat.toFloat()) }
    val animLon = remember { Animatable(uiState.centerLon.toFloat()) }
    val animBearing = remember { Animatable(0f) }

    // Update GPS dot and centering when location changes
    LaunchedEffect(location, uiState.navigationState?.currentWaypointIndex) {
        val loc = location ?: return@LaunchedEffect

        // Target values
        val targetLat = loc.latitude.toFloat()
        val targetLon = loc.longitude.toFloat()
        
        val nav = uiState.navigationState
        var targetBearing = if (nav != null && nav.isActive && nav.waypoints.isNotEmpty() && !nav.isOffTrack) {
            val idx = nav.currentWaypointIndex
            if (idx >= 0 && idx < nav.waypoints.size) {
                nav.waypoints[idx].bearingToNext
            } else {
                loc.bearing
            }
        } else {
            loc.bearing
        }

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

    // Update marker position continuously and map center/rotation
    SideEffect {
        val mv = mapViewRef.value ?: return@SideEffect
        locationMarker.value?.updatePosition(LatLong(currentLat, currentLon), currentBearing)
        
        if (uiState.followLocation) {
            mv.model.mapViewPosition.setCenter(LatLong(currentLat, currentLon))
            val pivotX = if (mv.width > 0) mv.width * 0.5f else 0f
            val pivotY = if (mv.height > 0) mv.height * 0.5f else 0f
            val targetRotation = if (uiState.mapRotationMode == MapRotationMode.HEADING_UP) -currentBearing else 0f
            mv.model.mapViewPosition.setRotation(Rotation(targetRotation, pivotX, pivotY))
        } else {
            val pivotX = if (mv.width > 0) mv.width * 0.5f else 0f
            val pivotY = if (mv.height > 0) mv.height * 0.5f else 0f
            val targetRotation = if (uiState.mapRotationMode == MapRotationMode.MANUAL) uiState.manualRotation else 0f
            mv.model.mapViewPosition.setRotation(Rotation(targetRotation, pivotX, pivotY))
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

    // Update GPX layer dynamically based on GPX changes and navigation progress
    LaunchedEffect(uiState.activeGpxFile, uiState.navigationState?.currentWaypointIndex, location) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        val gpx = uiState.activeGpxFile
        val nav = uiState.navigationState
        
        val pts = if (nav != null && nav.isActive) {
            val rawPoints = gpx?.trackPoints ?: emptyList()
            val nextWp = nav.currentWaypointIndex + 1
            if (nextWp < rawPoints.size) {
                val remaining = rawPoints.drop(nextWp).map { LatLong(it.lat, it.lon) }
                val userLocation = location
                if (userLocation != null) {
                    listOf(LatLong(userLocation.latitude, userLocation.longitude)) + remaining
                } else {
                    remaining
                }
            } else {
                emptyList()
            }
        } else {
            gpx?.trackPoints?.map { LatLong(it.lat, it.lon) } ?: emptyList()
        }

        if (trackLayer.value == null) {
            val layer = TrackMarkersLayer(pts, mv)
            mv.layerManager.layers.add(layer)
            trackLayer.value = layer
        } else {
            trackLayer.value?.updatePoints(pts)
        }
    }

    // Reload tile layer when active map file or theme changes
    LaunchedEffect(uiState.activeMapFile, uiState.mapTheme) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        reloadTileLayer(context, mv, uiState.activeMapFile, uiState.mapTheme, tileLayerRef)
    }

    // Update DotMarkLayer dynamically based on tappedPoint changes
    LaunchedEffect(uiState.tappedPoint) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        val pt = uiState.tappedPoint
        if (pt == null) {
            dotMarkLayer.value?.let { mv.layerManager.layers.remove(it); it.onDestroy() }
            dotMarkLayer.value = null
        } else {
            val latLong = LatLong(pt.lat, pt.lon)
            val currentLayer = dotMarkLayer.value
            if (currentLayer == null) {
                val layer = com.osm.wear.presentation.map.layers.DotMarkLayer(latLong, mv)
                mv.layerManager.layers.add(layer)
                dotMarkLayer.value = layer
            } else {
                currentLayer.updatePosition(latLong)
            }
        }
    }

    val gestureDetector = remember {
        android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val mv = mapViewRef.value ?: return
                val projection = org.mapsforge.map.util.MapViewProjection(mv)
                val latLong = projection.fromPixels(e.x.toDouble(), e.y.toDouble())
                if (latLong != null) {
                    vm.onMapTapped(latLong.latitude, latLong.longitude)
                }
            }
        })
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

                    // Initialize position from ViewModel state
                    mv.model.mapViewPosition.setCenter(LatLong(uiState.centerLat, uiState.centerLon))
                    mv.model.mapViewPosition.zoomLevel = uiState.zoomLevel.toByte()

                    // Load initial tile layer
                    uiState.activeMapFile?.let { f ->
                        reloadTileLayer(ctx, mv, f, uiState.mapTheme, tileLayerRef)
                    }

                    // Draw initial GPX
                    uiState.activeGpxFile?.let { gpx ->
                        val pts = gpx.trackPoints.map { LatLong(it.lat, it.lon) }
                        val layer = TrackMarkersLayer(pts, mv)
                        mv.layerManager.layers.add(layer)
                        trackLayer.value = layer
                    }

                    // Draw initial green dot (DotMarkLayer)
                    uiState.tappedPoint?.let { pt ->
                        val latLong = LatLong(pt.lat, pt.lon)
                        val layer = com.osm.wear.presentation.map.layers.DotMarkLayer(latLong, mv)
                        mv.layerManager.layers.add(layer)
                        dotMarkLayer.value = layer
                    }

                    // Bezel rotation → zoom
                    mv.setOnGenericMotionListener { _, event ->
                        if (event.action == MotionEvent.ACTION_SCROLL) {
                            val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                            if (scroll > 0) vm.zoomIn() else vm.zoomOut()
                            true
                        } else false
                    }

                    // Detect panning and rotation
                    var startX = 0f
                    var startY = 0f
                    var previousAngle = 0f
                    var isRotating = false
                    val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                    
                    mv.setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        }

                        if (event.pointerCount == 2) {
                            val dx = event.getX(1) - event.getX(0)
                            val dy = event.getY(1) - event.getY(0)
                            val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            
                            when (event.actionMasked) {
                                MotionEvent.ACTION_POINTER_DOWN -> {
                                    previousAngle = angle
                                    isRotating = true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (isRotating) {
                                        val delta = angle - previousAngle
                                        previousAngle = angle
                                        
                                        val currentRot = mv.model.mapViewPosition.rotation?.degrees ?: 0f
                                        val newRot = currentRot + delta
                                        
                                        // Update MapView rotation directly for smooth visual feedback
                                        val pivotX = if (mv.width > 0) mv.width * 0.5f else 0f
                                        val pivotY = if (mv.height > 0) mv.height * 0.5f else 0f
                                        mv.model.mapViewPosition.setRotation(Rotation(newRot, pivotX, pivotY))
                                        mv.postInvalidate()
                                        
                                        vm.onMapRotated(newRot)
                                    }
                                }
                                MotionEvent.ACTION_POINTER_UP -> {
                                    isRotating = false
                                }
                            }
                            return@setOnTouchListener true // Consume the event to prevent MapView from processing pinch-to-zoom
                        } else {
                            isRotating = false
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    startX = event.x
                                    startY = event.y
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.x - startX
                                    val dy = event.y - startY
                                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                        val mvPos = mv.model.mapViewPosition
                                        vm.onMapPanned(
                                            mvPos.center.latitude,
                                            mvPos.center.longitude
                                        )
                                    }
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
                    
                    val pivotX = if (mv.width > 0) mv.width * 0.5f else 0f
                    val pivotY = if (mv.height > 0) mv.height * 0.5f else 0f
                    val targetRotation = if (uiState.mapRotationMode == MapRotationMode.HEADING_UP) -currentBearing else 0f
                    mv.model.mapViewPosition.setRotation(Rotation(targetRotation, pivotX, pivotY))
                } else {
                    val pivotX = if (mv.width > 0) mv.width * 0.5f else 0f
                    val pivotY = if (mv.height > 0) mv.height * 0.5f else 0f
                    val targetRotation = if (uiState.mapRotationMode == MapRotationMode.MANUAL) uiState.manualRotation else 0f
                    mv.model.mapViewPosition.setRotation(Rotation(targetRotation, pivotX, pivotY))
                }
            }
        )

        // ── Map Controls (Curved Right side column) ─────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier.padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Settings Button (Top - shifted further left for curve)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = (-28).dp)
                        .background(Color(0x66000000), CircleShape)
                        .clickable { onOpenSettings(); lastInteractionTime = System.currentTimeMillis() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
                // Zoom In (Middle-Top - slightly shifted left for curve)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = (-8).dp)
                        .background(Color(0x66000000), CircleShape)
                        .clickable { vm.zoomIn(); lastInteractionTime = System.currentTimeMillis() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
                // Zoom Out (Middle-Bottom - stays at the edge)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = (-8).dp)
                        .background(Color(0x66000000), CircleShape)
                        .clickable { vm.zoomOut(); lastInteractionTime = System.currentTimeMillis() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
                // Center on Location (Bottom - slightly shifted left for curve)
                val buttonBgColor = if (!uiState.followLocation) {
                    Color(0x66000000) // same background as other buttons in free mode
                } else {
                    Color(0x661565C0) // transparent blue in other modes
                }
                val buttonIcon = if (uiState.followLocation && uiState.mapRotationMode == MapRotationMode.HEADING_UP) {
                    Icons.Default.Navigation
                } else {
                    Icons.Default.MyLocation
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = (-28).dp)
                        .background(buttonBgColor, CircleShape)
                        .clickable { vm.centerOnLocation(); lastInteractionTime = System.currentTimeMillis() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = "Center on Location",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
            }
        }

        // ── Navigation overlay ────────────────────────────────────────────────
        if (navState != null && navState.isActive && navState.waypoints.isNotEmpty()) {
            NavigationOverlay(
                navState = navState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
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
                color = Color(0x66000000),
                shape = CircleShape
            )
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = arrow, 
            fontSize = 24.sp, 
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = Color.Yellow
        )
        Text(distText, fontSize = 15.sp, color = Color.White)
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
    mv.mapScaleBar.isVisible = false
    mv.setBuiltInZoomControls(false)
    return mv
}

private fun reloadTileLayer(
    context: Context,
    mv: MapView,
    mapFile: File?,
    theme: MapTheme,
    ref: MutableState<TileRendererLayer?>
) {
    try {
        ref.value?.let { mv.layerManager.layers.remove(it); it.onDestroy() }
        ref.value = null
        if (mapFile == null || !mapFile.exists()) return
        val cache = AndroidUtil.createTileCache(
            context, "mapcache",
            mv.model.displayModel.tileSize,
            1f,
            mv.model.frameBufferModel.overdrawFactor
        )
        val renderTheme = when (theme) {
            MapTheme.BIKER -> MapsforgeThemes.BIKER
            MapTheme.DARK -> MapsforgeThemes.DARK
            MapTheme.DEFAULT -> MapsforgeThemes.DEFAULT
            MapTheme.HILLSHADING -> MapsforgeThemes.HILLSHADING
            MapTheme.INDIGO -> MapsforgeThemes.INDIGO
            MapTheme.MOTORIDER -> MapsforgeThemes.MOTORIDER
            MapTheme.OSMARENDER -> MapsforgeThemes.OSMARENDER
        }
        val layer = TileRendererLayer(
            cache,
            MapFile(mapFile),
            mv.model.mapViewPosition,
            AndroidGraphicFactory.INSTANCE
        ).apply { setXmlRenderTheme(renderTheme) }
        mv.layerManager.layers.add(0, layer)
        ref.value = layer
    } catch (e: Exception) {
        android.util.Log.e("MainMapScreen", "Failed to reload tile layer", e)
    }
}
