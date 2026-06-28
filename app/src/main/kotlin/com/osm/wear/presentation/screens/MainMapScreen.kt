package com.osm.wear.presentation.screens

import com.osm.wear.view_models.*

import android.content.Context
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import com.osm.wear.presentation.theme.AppDimensions
import com.osm.wear.presentation.theme.MapLayerColors
import com.osm.wear.presentation.theme.MapUiAlpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.osm.wear.models.NavigationState
import com.osm.wear.presentation.map.layers.LocationArrowLayer
import com.osm.wear.presentation.map.layers.TrackMarkersLayer
import com.osm.wear.presentation.map.layers.MarkerLayer
import com.osm.wear.presentation.map.layers.AddressPopupLayer
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Rotation
import com.osm.wear.models.enums.MapRotationMode
import com.osm.wear.models.enums.MapTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import java.io.File

@Composable
fun MainMapScreen(
    mapVm: MapViewModel,
    markerVm: MarkerViewModel,
    navVm: NavigationViewModel,
    regionsVm: RegionsViewModel,
    gpxVm: GpxFilesViewModel,
    settingsVm: SettingsViewModel,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val uiState  by mapVm.uiState.collectAsStateWithLifecycle()
    val location by mapVm.currentLocation.collectAsStateWithLifecycle()
    val markerState by markerVm.uiState.collectAsStateWithLifecycle()
    
    val navState by navVm.navigationState.collectAsStateWithLifecycle()
    val activeMapFile by regionsVm.activeMapFile.collectAsStateWithLifecycle()
    val activeGpxFile by gpxVm.activeGpxFile.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    
    val pointMarkColor = MapLayerColors.DOT_MARK_FILL

    val focusRequester = remember { FocusRequester() }

    BackHandler { onOpenMenu() }

    // Request focus for rotary events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }
    val controlsAlpha = if (controlsVisible) 1f else 0f

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
    val markerLayer    = remember { mutableStateOf<MarkerLayer?>(null) }
    val popupLayerRef   = remember { mutableStateOf<AddressPopupLayer?>(null) }
    val parentLayoutRef = remember { mutableStateOf<android.widget.FrameLayout?>(null) }

    // Programmatically center the map when centerEvents emits a point
    LaunchedEffect(Unit) {
        mapVm.centerEvents.collect { latLong ->
            mapViewRef.value?.model?.mapViewPosition?.setCenter(latLong)
        }
    }

    // ── Smooth Location Animation ──────────────────────────────────────────
    // We animate Lat/Long and Bearing to interpolate between GPS updates.
    val animLat = remember { Animatable(uiState.centerLat.toFloat()) }
    val animLon = remember { Animatable(uiState.centerLon.toFloat()) }
    val animBearing = remember { Animatable(0f) }

    // Update GPS dot and centering when location changes
    LaunchedEffect(location, navState?.currentWaypointIndex) {
        val loc = location ?: return@LaunchedEffect

        // Target values
        val targetLat = loc.latitude.toFloat()
        val targetLon = loc.longitude.toFloat()
        
        val nav = navState
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
        
        val (pivotX, pivotY) = mapVm.getMapPivot(mv.width, mv.height)

        if (uiState.followLocation) {
            mv.model.mapViewPosition.setCenter(LatLong(currentLat, currentLon))
        }
        
        val targetRotation = when (uiState.mapRotationMode) {
            MapRotationMode.HEADING_UP -> -currentBearing
            MapRotationMode.MANUAL -> uiState.manualRotation
            else -> 0f
        }
        mv.model.mapViewPosition.setRotation(Rotation(targetRotation, pivotX, pivotY))
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
    LaunchedEffect(activeGpxFile, navState?.currentWaypointIndex, location) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        val gpx = activeGpxFile
        val nav = navState
        
        val pts = if (nav != null && nav.isActive) {
            val rawPoints = gpx?.trackPoints ?: emptyList()
            val currentWp = nav.waypoints.getOrNull(nav.currentWaypointIndex)
            val nextWp = (currentWp?.rawIndex ?: nav.currentWaypointIndex) + 1
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
    LaunchedEffect(activeMapFile, settingsState.mapTheme) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        reloadTileLayer(context, mv, activeMapFile, settingsState.mapTheme, tileLayerRef)
    }

    // Update MarkerLayer and AddressPopupLayer dynamically based on tappedPoint changes
    LaunchedEffect(markerState.tappedPoint, parentLayoutRef.value) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        val pt = markerState.tappedPoint
        if (pt == null) {
            markerLayer.value?.let { mv.layerManager.layers.remove(it); it.onDestroy() }
            markerLayer.value = null
            
            popupLayerRef.value?.let { mv.layerManager.layers.remove(it); it.onDestroy() }
            popupLayerRef.value = null
        } else {
            val latLong = LatLong(pt.lat, pt.lon)
            
            // Handle MarkerLayer
            val currentDotLayer = markerLayer.value
            if (currentDotLayer == null) {
                val layer = MarkerLayer(latLong, mv, pointMarkColor)
                mv.layerManager.layers.add(layer)
                markerLayer.value = layer
            } else {
                currentDotLayer.updatePosition(latLong)
                currentDotLayer.updateColor(pointMarkColor)
            }

            // Handle AddressPopupLayer
            val parentLayout = parentLayoutRef.value
            if (popupLayerRef.value == null && parentLayout != null) {
                val layer = AddressPopupLayer(
                    context = context,
                    mv = mv,
                    parentLayout = parentLayout,
                    uiStateFlow = markerVm.uiState,
                    controlsVisibleState = derivedStateOf { controlsVisible },
                    zoomLevelState = derivedStateOf { uiState.zoomLevel },
                    onInteraction = {
                        lastInteractionTime = System.currentTimeMillis()
                    }
                )
                mv.layerManager.layers.add(layer)
                popupLayerRef.value = layer
            }
        }
    }

    val gestureDetector = remember {
        android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val mv = mapViewRef.value ?: return
                
                // Account for map rotation when calculating the tapped coordinate
                val rot = mv.model.mapViewPosition.rotation?.degrees ?: 0f
                val (finalX, finalY) = mapVm.getUnrotatedTapPoint(e.x, e.y, mv.width, mv.height, rot)

                val projection = org.mapsforge.map.util.MapViewProjection(mv)
                val latLong = projection.fromPixels(finalX, finalY)
                if (latLong != null) {
                    markerVm.onMapTapped(latLong.latitude, latLong.longitude)
                }
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onRotaryScrollEvent {
                if (it.verticalScrollPixels > 0) mapVm.zoomOut() else mapVm.zoomIn()
                true
            }
    ) {

        // ── Mapsforge MapView & Overlay Popup ────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val parentLayout = android.widget.FrameLayout(ctx)

                val mv = createMapView(ctx).apply {
                    // Initialize position from ViewModel state
                    model.mapViewPosition.setCenter(LatLong(uiState.centerLat, uiState.centerLon))
                    model.mapViewPosition.zoomLevel = uiState.zoomLevel.toByte()
                }
                mapViewRef.value = mv

                parentLayout.addView(mv, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))

                parentLayoutRef.value = parentLayout

                // Load initial tile layer
                activeMapFile?.let { f ->
                    reloadTileLayer(ctx, mv, f, settingsState.mapTheme, tileLayerRef)
                }

                // Draw initial GPX
                activeGpxFile?.let { gpx ->
                    val pts = gpx.trackPoints.map { LatLong(it.lat, it.lon) }
                    val layer = TrackMarkersLayer(pts, mv)
                    mv.layerManager.layers.add(layer)
                    trackLayer.value = layer
                }

                // Draw initial dot (MarkerLayer) & AddressPopupLayer
                markerState.tappedPoint?.let { pt ->
                    val latLong = LatLong(pt.lat, pt.lon)
                    val dotLayer = MarkerLayer(latLong, mv, pointMarkColor)
                    mv.layerManager.layers.add(dotLayer)
                    markerLayer.value = dotLayer

                    val popupLayer = AddressPopupLayer(
                        context = ctx,
                        mv = mv,
                        parentLayout = parentLayout,
                        uiStateFlow = markerVm.uiState,
                        controlsVisibleState = derivedStateOf { controlsVisible },
                        zoomLevelState = derivedStateOf { uiState.zoomLevel },
                        onInteraction = {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )
                    mv.layerManager.layers.add(popupLayer)
                    popupLayerRef.value = popupLayer
                }

                // Bezel rotation → zoom
                mv.setOnGenericMotionListener { _, event ->
                    if (event.action == MotionEvent.ACTION_SCROLL) {
                        val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                        if (scroll > 0) mapVm.zoomIn() else mapVm.zoomOut()
                        true
                    } else false
                }

                // Detect panning and rotation
                var startX = 0f
                var startY = 0f
                val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

                mv.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }

                    if (event.pointerCount == 2) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                mapVm.onPinchDown(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val currentRot = mv.model.mapViewPosition.rotation?.degrees ?: 0f
                                val newRot = mapVm.onPinchMove(event.getX(0), event.getY(0), event.getX(1), event.getY(1), currentRot)
                                if (newRot != null) {
                                    val (pivotX, pivotY) = mapVm.getMapPivot(mv.width, mv.height)
                                    mv.model.mapViewPosition.setRotation(Rotation(newRot, pivotX, pivotY))
                                    mv.postInvalidate()
                                }
                            }
                            MotionEvent.ACTION_POINTER_UP -> {
                                mapVm.onPinchUp()
                            }
                        }
                        return@setOnTouchListener true
                    } else {
                        mapVm.onPinchUp()
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
                                    mapVm.onMapPanned(
                                        mvPos.center.latitude,
                                        mvPos.center.longitude
                                    )
                                }
                            }
                        }
                    }
                    false
                }

                parentLayout
            },
            update = { parentLayout ->
                val mv = parentLayout.getChildAt(0) as MapView

                // Ensure initial marker exists
                if (locationMarker.value == null && location != null) {
                    val loc = location!!
                    val newMarker = LocationArrowLayer(LatLong(loc.latitude, loc.longitude), loc.bearing, mv)
                    mv.layerManager.layers.add(newMarker)
                    locationMarker.value = newMarker
                }

                val (pivotX, pivotY) = mapVm.getMapPivot(mv.width, mv.height)

                if (uiState.followLocation) {
                    mv.model.mapViewPosition.setCenter(LatLong(currentLat, currentLon))
                    mv.model.mapViewPosition.zoomLevel = uiState.zoomLevel.toByte()
                }

                val targetRotation = when (uiState.mapRotationMode) {
                    MapRotationMode.HEADING_UP -> -currentBearing
                    MapRotationMode.MANUAL -> uiState.manualRotation
                    else -> 0f
                }
                mv.model.mapViewPosition.setRotation(Rotation(targetRotation, pivotX, pivotY))
            }
        )

        // ── Map Controls (Curved Right side column) ─────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = AppDimensions.MapControlMarginEnd)
                .graphicsLayer {
                    alpha = controlsAlpha
                },
            verticalArrangement = Arrangement.spacedBy(AppDimensions.MapControlSpacing),
            horizontalAlignment = Alignment.End
        ) {
            // Settings Button (Top - shifted further left for curve)
            Box(
                modifier = Modifier
                    .size(AppDimensions.MapControlBox)
                    .offset(x = AppDimensions.MapControlOffsetOuter)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha), CircleShape)
                    .clickable(enabled = controlsVisible) { onOpenMenu(); lastInteractionTime = System.currentTimeMillis() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(AppDimensions.IconSmall),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // Zoom In (Middle-Top - slightly shifted left for curve)
            Box(
                modifier = Modifier
                    .size(AppDimensions.MapControlBox)
                    .offset(x = AppDimensions.MapControlOffsetInner)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha), CircleShape)
                    .clickable(enabled = controlsVisible) { mapVm.zoomIn(); lastInteractionTime = System.currentTimeMillis() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(AppDimensions.IconSmall),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // Zoom Out (Middle-Bottom - stays at the edge)
            Box(
                modifier = Modifier
                    .size(AppDimensions.MapControlBox)
                    .offset(x = AppDimensions.MapControlOffsetInner)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha), CircleShape)
                    .clickable(enabled = controlsVisible) { mapVm.zoomOut(); lastInteractionTime = System.currentTimeMillis() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(AppDimensions.IconSmall),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // Center on Location (Bottom - slightly shifted left for curve)
            val buttonBgColor = if (!uiState.followLocation) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = MapUiAlpha)
            }
            val buttonIconColor = if (!uiState.followLocation) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
            val buttonIcon = if (uiState.followLocation && uiState.mapRotationMode == MapRotationMode.HEADING_UP) {
                Icons.Default.Navigation
            } else {
                Icons.Default.MyLocation
            }
            Box(
                modifier = Modifier
                    .size(AppDimensions.MapControlBox)
                    .offset(x = AppDimensions.MapControlOffsetOuter)
                    .background(buttonBgColor, CircleShape)
                    .clickable(enabled = controlsVisible) { mapVm.centerOnLocation(); lastInteractionTime = System.currentTimeMillis() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = buttonIcon,
                    contentDescription = "Center on Location",
                    modifier = Modifier.size(AppDimensions.IconSmall),
                    tint = buttonIconColor
                )
            }
        }

        // The popup is rendered natively as an overlay sibling to MapView to move perfectly together with the map

        // ── Navigation overlay ────────────────────────────────────────────────
        val currentNavState = navState
        if (currentNavState != null && currentNavState.isActive && currentNavState.waypoints.isNotEmpty()) {
            NavigationOverlay(
                navState = currentNavState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppDimensions.NavOverlayPaddingBottom)
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

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha),
                shape = CircleShape
            )
            .padding(
                horizontal = AppDimensions.NavOverlayPaddingHorizontal,
                vertical = AppDimensions.NavOverlayPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.NavOverlaySpacing)
    ) {
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = "Turn direction",
            modifier = Modifier.size(AppDimensions.NavOverlayIconSize).rotate(navState.bearingToNextTurn),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = distText, 
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (navState.isOffTrack) {
            Text(
                text = "⚠",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
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



