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
import kotlin.jvm.Volatile

/**
 * Renders a distinct green dot with a black border at the tapped position.
 */
class DotMarkLayer(
    @Volatile private var latLong: LatLong,
    private val mv: MapView
) : Layer() {

    private val fillPaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.GREEN)
        setStyle(Style.FILL)
    }

    private val strokePaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.BLACK)
        strokeWidth = 3f
        setStyle(Style.STROKE)
    }

    private val dotRadius = 10f

    fun updatePosition(newLatLong: LatLong) {
        this.latLong = newLatLong
        requestRedraw()
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val loc = this.latLong
        val tileSize = mv.model.displayModel.tileSize
        val pixelX = MercatorProjection.longitudeToPixelX(loc.longitude, zoomLevel, tileSize)
        val pixelY = MercatorProjection.latitudeToPixelY(loc.latitude, zoomLevel, tileSize)

        val x = (pixelX - topLeftPoint.x).toInt()
        val y = (pixelY - topLeftPoint.y).toInt()

        canvas.drawCircle(x, y, dotRadius.toInt(), fillPaint)
        canvas.drawCircle(x, y, dotRadius.toInt(), strokePaint)
    }
}
