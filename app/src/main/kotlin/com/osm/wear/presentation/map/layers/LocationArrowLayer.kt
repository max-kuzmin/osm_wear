package com.osm.wear.presentation.map.layers

import android.graphics.Color
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.core.util.MercatorProjection

/**
 * A map layer that draws a directional arrow representing the user's current location and bearing.
 * Optimized for Wear OS by caching the arrow path and avoiding allocations during draw.
 */
class LocationArrowLayer(
    private var latLong: LatLong,
    private var bearing: Float,
    private val mv: MapView
) : Layer() {

    private val fillPaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.argb(220, 30, 136, 229))
        setStyle(Style.FILL)
    }

    private val strokePaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.WHITE)
        strokeWidth = 3f
        setStyle(Style.STROKE)
        setStrokeCap(Cap.ROUND)
    }

    private val arrowPath = AndroidGraphicFactory.INSTANCE.createPath()
    private val arrowSize = 25f

    init {
        // Pre-compute path relative to (0,0)
        arrowPath.moveTo(0f, -arrowSize)
        arrowPath.lineTo(-arrowSize * 0.8f, arrowSize * 0.8f)
        arrowPath.lineTo(0f, arrowSize * 0.4f)
        arrowPath.lineTo(arrowSize * 0.8f, arrowSize * 0.8f)
        arrowPath.close()
    }

    /**
     * Updates the position and bearing of the arrow and requests a redraw.
     */
    fun updatePosition(newLatLong: LatLong, newBearing: Float) {
        this.latLong = newLatLong
        this.bearing = newBearing
        requestRedraw()
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val tileSize = mv.model.displayModel.tileSize
        val pixelX = MercatorProjection.longitudeToPixelX(latLong.longitude, zoomLevel, tileSize)
        val pixelY = MercatorProjection.latitudeToPixelY(latLong.latitude, zoomLevel, tileSize)

        val x = (pixelX - topLeftPoint.x).toFloat()
        val y = (pixelY - topLeftPoint.y).toFloat()

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(bearing, 0f, 0f)

        canvas.drawPath(arrowPath, fillPaint)
        canvas.drawPath(arrowPath, strokePaint)

        canvas.restore()
    }
}
