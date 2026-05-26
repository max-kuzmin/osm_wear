package com.osm.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.GpxTrack
import com.osm.wear.domain.model.UserLocation
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.File

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenMenu: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeMapFile by viewModel.activeMapFile.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val gpxTracks by viewModel.gpxTracks.collectAsStateWithLifecycle()
    val zoomLevel by viewModel.zoomLevel.collectAsStateWithLifecycle()
    val centerLat by viewModel.centerLat.collectAsStateWithLifecycle()
    val centerLon by viewModel.centerLon.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try {
            AndroidGraphicFactory.createInstance(
                context.applicationContext as android.app.Application
            )
        } catch (_: Exception) { /* Already initialized */ }
        viewModel.startLocationTracking()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        if (activeMapFile != null) {
            MapsforgeMapView(
                mapFile = activeMapFile!!,
                zoomLevel = zoomLevel,
                centerLat = centerLat,
                centerLon = centerLon,
                userLocation = userLocation,
                gpxTracks = gpxTracks,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No offline map.\nDownload a region first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Top-left: Menu button
        CompactButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(28.dp)
        ) {
            Text("☰", style = MaterialTheme.typography.labelSmall)
        }

        // Right-center: Zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompactButton(
                onClick = { viewModel.zoomIn() },
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", style = MaterialTheme.typography.labelSmall)
            }
            CompactButton(
                onClick = { viewModel.zoomOut() },
                modifier = Modifier.size(32.dp)
            ) {
                Text("−", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Bottom-center: Recenter button
        if (userLocation != null) {
            CompactButton(
                onClick = { viewModel.centerOnUserLocation() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(32.dp)
            ) {
                Text("◎", style = MaterialTheme.typography.labelSmall)
            }
        }

        // GPS searching indicator
        if (userLocation == null) {
            Text(
                text = "GPS…",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFCC00),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun MapsforgeMapView(
    mapFile: File,
    zoomLevel: Int,
    centerLat: Double,
    centerLon: Double,
    userLocation: UserLocation?,
    gpxTracks: List<GpxTrack>,
    modifier: Modifier = Modifier
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var tileCache by remember { mutableStateOf<TileCache?>(null) }
    val gpxPolylines = remember { mutableListOf<Polyline>() }
    var locationCircle by remember { mutableStateOf<Circle?>(null) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mapViewRef = mv
                mv.isClickable = true
                mv.mapScaleBar.isVisible = false

                val cache = AndroidUtil.createTileCache(
                    ctx, "maincache",
                    mv.model.displayModel.tileSize,
                    1f,
                    mv.model.frameBufferModel.overdrawFactor
                )
                tileCache = cache

                val layer = TileRendererLayer(
                    cache,
                    MapFile(mapFile),
                    mv.model.mapViewPosition,
                    AndroidGraphicFactory.INSTANCE
                )
                layer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER)
                mv.layerManager.layers.add(layer)

                mv.model.mapViewPosition.setCenter(LatLong(centerLat, centerLon))
                mv.model.mapViewPosition.zoomLevel = zoomLevel.toByte()
            }
        },
        update = { mv ->
            mv.model.mapViewPosition.setCenter(LatLong(centerLat, centerLon))
            mv.model.mapViewPosition.zoomLevel = zoomLevel.toByte()

            val layers = mv.layerManager.layers

            // Update location circle
            locationCircle?.let { layers.remove(it) }
            locationCircle = null
            userLocation?.let { loc ->
                val fillPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    color = android.graphics.Color.argb(180, 0, 120, 255)
                    style = org.mapsforge.core.graphics.Style.FILL
                }
                val strokePaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    color = android.graphics.Color.WHITE
                    style = org.mapsforge.core.graphics.Style.STROKE
                    strokeWidth = 2f
                }
                val circle = Circle(
                    LatLong(loc.latitude, loc.longitude),
                    loc.accuracy,
                    fillPaint,
                    strokePaint
                )
                locationCircle = circle
                layers.add(circle)
            }

            // Update GPX polylines
            gpxPolylines.forEach { layers.remove(it) }
            gpxPolylines.clear()
            gpxTracks.filter { it.isVisible }.forEach { track ->
                val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    color = android.graphics.Color.argb(220, 255, 80, 0)
                    style = org.mapsforge.core.graphics.Style.STROKE
                    strokeWidth = 4f
                }
                val polyline = Polyline(paint, AndroidGraphicFactory.INSTANCE)
                track.points.forEach { pt ->
                    polyline.latLongs.add(LatLong(pt.latitude, pt.longitude))
                }
                gpxPolylines.add(polyline)
                layers.add(polyline)
            }

            mv.layerManager.redrawLayers()
        },
        modifier = modifier
    )

    DisposableEffect(mapFile.path) {
        onDispose {
            mapViewRef?.destroyAll()
            tileCache?.destroy()
            mapViewRef = null
            tileCache = null
            locationCircle = null
            gpxPolylines.clear()
        }
    }
}
