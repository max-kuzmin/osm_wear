package com.osm.wear.presentation.map.layers

import android.graphics.Color
import com.osm.wear.presentation.theme.MapLayerColors
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
 * Renders a distinct dot with a black border at the tapped position,
 * matching the button background color.
 */
class MarkerLayer(
    @Volatile private var latLong: LatLong,
    private val mv: MapView,
    private var fillColor: Int
) : Layer() {

    private val fillPaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(fillColor)
        setStyle(Style.FILL)
    }

    private val strokePaint: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(MapLayerColors.DOT_MARK_STROKE)
        strokeWidth = 3f
        setStyle(Style.STROKE)
    }

    private val dotRadius = 10f

    fun updatePosition(newLatLong: LatLong) {
        this.latLong = newLatLong
        requestRedraw()
    }

    fun updateColor(newColor: Int) {
        this.fillColor = newColor
        fillPaint.setColor(newColor)
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
