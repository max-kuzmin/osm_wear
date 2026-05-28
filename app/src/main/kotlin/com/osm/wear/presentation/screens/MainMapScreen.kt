package com.osm.wear.presentation.screens

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.NavigationState
import com.osm.wear.domain.model.UserLocation
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
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

    // Stable references to Mapsforge layers
    val mapViewRef      = remember { mutableStateOf<MapView?>(null) }
    val locationCircle  = remember { mutableStateOf<Circle?>(null) }
    val gpxPolyline     = remember { mutableStateOf<Polyline?>(null) }
    val tileLayerRef    = remember { mutableStateOf<TileRendererLayer?>(null) }

    // Update GPS dot when location changes
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        val mv  = mapViewRef.value ?: return@LaunchedEffect
        updateLocationCircle(mv, loc, locationCircle)
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

    Box(modifier = Modifier.fillMaxSize()) {

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
                            val pos = mv.model.mapViewPosition
                            val newZoom = (pos.zoomLevel.toInt() + if (scroll > 0) 1 else -1)
                                .coerceIn(pos.zoomLevelMin.toInt(), pos.zoomLevelMax.toInt())
                            pos.zoomLevel = newZoom.toByte()
                            true
                        } else false
                    }
                }
            },
            update = { /* layers updated via LaunchedEffect */ }
        )

        // ── Settings button (top-right) ──────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xCC000000)
                )
            ) {
                Text("⚙", fontSize = 16.sp)
            }
        }

        // ── GPS centre button (bottom-centre) ────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Button(
                onClick = {
                    location?.let { loc ->
                        mapViewRef.value?.model?.mapViewPosition
                            ?.setCenter(LatLong(loc.latitude, loc.longitude))
                    }
                    vm.centerOnLocation()
                },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xCC1565C0)
                )
            ) {
                Text("◎", fontSize = 16.sp)
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
                color = androidx.compose.ui.graphics.Color(0xDD000000),
                shape = CircleShape
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(arrow, fontSize = 20.sp, color = androidx.compose.ui.graphics.Color.Yellow)
        Text(distText, fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White)
        if (navState.isOffTrack) {
            Text("⚠", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Red)
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
    ).apply { setXmlRenderTheme(InternalRenderTheme.OSMARENDER) }
    mv.layerManager.layers.add(0, layer)
    ref.value = layer
    mv.model.mapViewPosition.zoomLevel = 12
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
        setColor(Color.argb(200, 255, 80, 0))
        strokeWidth = 6f
        setStyle(Style.STROKE)
    }
    val poly = Polyline(paint, AndroidGraphicFactory.INSTANCE)
    poly.latLongs.addAll(points)
    mv.layerManager.layers.add(poly)
    ref.value = poly
}

private fun updateLocationCircle(
    mv: MapView,
    loc: UserLocation,
    ref: MutableState<Circle?>
) {
    ref.value?.let { mv.layerManager.layers.remove(it) }
    val fill: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.argb(180, 30, 136, 229))
        setStyle(Style.FILL)
    }
    val stroke: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.WHITE)
        strokeWidth = 3f
        setStyle(Style.STROKE)
    }
    val circle = Circle(LatLong(loc.latitude, loc.longitude), loc.accuracy.coerceAtLeast(10f), fill, stroke)
    mv.layerManager.layers.add(circle)
    ref.value = circle
}
