package com.osm.wear.presentation.map.layers

import android.graphics.Color
import org.mapsforge.core.graphics.Canvas
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
 * Renders the GPX track polyline and an end-of-track marker.
 */
class TrackMarkersLayer(
    private var points: List<LatLong>,
    private val mv: MapView
) : Layer() {

    private val linePaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.argb(200, 255, 80, 0))
        strokeWidth = 6f
        setStyle(Style.STROKE)
    }

    private val endPaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.RED)
        setStyle(Style.FILL)
    }

    private val strokePaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.BLACK)
        strokeWidth = 2f
        setStyle(Style.STROKE)
    }

    private val endRadius = 8f

    fun updatePoints(newPoints: List<LatLong>) {
        this.points = newPoints
        requestRedraw()
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        if (points.isEmpty()) return
        
        val tileSize = mv.model.displayModel.tileSize

        // 1. Draw Polyline
        var prevX = 0
        var prevY = 0
        for (i in points.indices) {
            val pt = points[i]
            val px = MercatorProjection.longitudeToPixelX(pt.longitude, zoomLevel, tileSize)
            val py = MercatorProjection.latitudeToPixelY(pt.latitude, zoomLevel, tileSize)
            
            val x = (px - topLeftPoint.x).toInt()
            val y = (py - topLeftPoint.y).toInt()

            if (i > 0) {
                canvas.drawLine(prevX, prevY, x, y, linePaint)
            }
            prevX = x
            prevY = y
        }

        // 2. Draw End Marker
        drawEndCircle(canvas, points.last(), zoomLevel, tileSize, topLeftPoint)
    }

    private fun drawEndCircle(
        canvas: Canvas,
        latLong: LatLong,
        zoomLevel: Byte,
        tileSize: Int,
        topLeftPoint: Point
    ) {
        val pixelX = MercatorProjection.longitudeToPixelX(latLong.longitude, zoomLevel, tileSize)
        val pixelY = MercatorProjection.latitudeToPixelY(latLong.latitude, zoomLevel, tileSize)

        val x = (pixelX - topLeftPoint.x).toInt()
        val y = (pixelY - topLeftPoint.y).toInt()

        canvas.drawCircle(x, y, endRadius.toInt(), endPaint)
        canvas.drawCircle(x, y, endRadius.toInt(), strokePaint)
    }
}
