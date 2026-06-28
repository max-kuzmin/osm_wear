package com.osm.wear.presentation.map.layers

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.osm.wear.presentation.theme.MapUiAlpha
import com.osm.wear.view_models.MapUiState
import kotlinx.coroutines.flow.StateFlow
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.util.MapViewProjection

/**
 * Custom Mapsforge Layer encapsulating the native FrameLayout overlay container,
 * ComposeView rendering, and dynamic positioning above the tapped blue dot marker.
 */
class AddressPopupLayer(
    private val context: Context,
    private val mv: MapView,
    private val parentLayout: FrameLayout,
    private val uiStateFlow: StateFlow<MapUiState>,
    private val controlsVisibleState: State<Boolean>,
    private val zoomLevelState: State<Int>,
    private val onInteraction: () -> Unit
) : Layer() {

    companion object {
        private const val ADDRESS_MAX_LINES = 5
    }

    private val popupContainer: FrameLayout = FrameLayout(context).apply {
        visibility = View.GONE
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updatePopupPosition()
        }
    }

    private val composeView: ComposeView = ComposeView(context).apply {
        setContent {
            val uiState by uiStateFlow.collectAsState()
            val controlsVisible by controlsVisibleState
            val zoomLevel by zoomLevelState
            
            val tappedPoint = uiState.tappedPoint
            val isVisible = controlsVisible && tappedPoint != null && !uiState.isResolvingAddress && zoomLevel >= 15
            
            LaunchedEffect(isVisible) {
                popupContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
                updatePopupPosition()
            }
            
            if (tappedPoint != null) {
                Box(
                    modifier = Modifier
                        .widthIn(max = (LocalConfiguration.current.screenWidthDp * 2 / 3).dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (uiState.isResolvingAddress) {
                            Text(
                                text = "Resolving...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            val tappedName = uiState.tappedPointName
                            val tappedAddress = uiState.tappedPointAddress

                            if (!tappedName.isNullOrBlank()) {
                                // Line 1: name
                                Text(
                                    text = tappedName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Line 2: full address (strip leading name prefix), wraps naturally up to 2 lines
                                var cleanAddr = tappedAddress ?: ""
                                val name = tappedName.trim()
                                if (cleanAddr.startsWith(name, ignoreCase = true)) {
                                    cleanAddr = cleanAddr.substring(name.length).trim().removePrefix(",").trim()
                                }
                                if (cleanAddr.isNotBlank()) {
                                    Text(
                                        text = cleanAddr,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        maxLines = ADDRESS_MAX_LINES,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (!tappedAddress.isNullOrBlank()) {
                                // No name: show full address with wrap up to 2 lines
                                Text(
                                    text = tappedAddress,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = ADDRESS_MAX_LINES,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "%.5f, %.5f".format(tappedPoint.lat, tappedPoint.lon),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        popupContainer.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        // Add container to the parentLayout (which contains MapView and overlays)
        mv.post {
            parentLayout.addView(
                popupContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    fun updatePopupPosition() {
        val pt = uiStateFlow.value.tappedPoint ?: return
        val loc = LatLong(pt.lat, pt.lon)
        val projection = MapViewProjection(mv)
        val screenPos = projection.toPixels(loc)
        if (screenPos != null) {
            popupContainer.translationX = screenPos.x.toFloat() - popupContainer.width / 2f
            // The blue dot (MarkerLayer) has a radius of 10 pixels.
            // To position the popup exactly 8 pixels above the top edge of the blue dot:
            // Y = screenPos.y - dotRadius (10px) - spacing (8px) - popupHeight
            // Y = screenPos.y - 18 - popupHeight
            popupContainer.translationY = screenPos.y.toFloat() - popupContainer.height - 18f
        }
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        mv.post {
            updatePopupPosition()
        }
    }

    override fun onDestroy() {
        mv.post {
            parentLayout.removeView(popupContainer)
        }
        super.onDestroy()
    }
}
