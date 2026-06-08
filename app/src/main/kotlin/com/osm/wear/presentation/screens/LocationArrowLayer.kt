package com.osm.wear.presentation.screens

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

class LocationArrowLayer(var latLong: LatLong, var bearing: Float, private val mv: MapView) : Layer() {
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

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val tileSize = mv.model.displayModel.tileSize
        val pixelX = MercatorProjection.longitudeToPixelX(latLong.longitude, zoomLevel, tileSize)
        val pixelY = MercatorProjection.latitudeToPixelY(latLong.latitude, zoomLevel, tileSize)
        
        val x = (pixelX - topLeftPoint.x).toFloat()
        val y = (pixelY - topLeftPoint.y).toFloat()
        
        canvas.save()
        canvas.rotate(bearing, x, y)
        
        val size = 25f
        val path = AndroidGraphicFactory.INSTANCE.createPath()
        path.moveTo(x, y - size)
        path.lineTo(x - size * 0.8f, y + size * 0.8f)
        path.lineTo(x, y + size * 0.4f)
        path.lineTo(x + size * 0.8f, y + size * 0.8f)
        path.close()
        
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        
        canvas.restore()
    }
}
