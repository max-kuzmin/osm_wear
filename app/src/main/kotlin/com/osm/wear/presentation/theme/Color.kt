package com.osm.wear.presentation.theme

import androidx.compose.ui.graphics.Color

// Compose UI Colors
val PrimaryColor = Color(0xFF1E88E5) // Blue (matching map marker/dot)
val OnPrimaryColor = Color.White
val BackgroundColor = Color.Black
val SurfaceContainerColor = Color(0xFF1E1E1E) // Dark Grey for OLED
val OnSurfaceColor = Color(0xFFE3E3E3)
val OnSurfaceVariantColor = Color(0xFF9E9E9E)
val ErrorColor = Color(0xFFD32F2F)
val OnErrorColor = Color.White
val ErrorContainerColor = Color(0xFFB71C1C)
val OnErrorContainerColor = Color.White

// Android Graphics Colors (used in Mapsforge overlays)
object MapLayerColors {
    val LOCATION_ARROW_FILL = android.graphics.Color.argb(220, 30, 136, 229)
    const val LOCATION_ARROW_STROKE = android.graphics.Color.WHITE
    
    val DOT_MARK_FILL = android.graphics.Color.argb(220, 30, 136, 229)
    const val DOT_MARK_STROKE = android.graphics.Color.WHITE
    
    val TRACK_LINE = android.graphics.Color.argb(200, 255, 80, 0)
    const val TRACK_END = android.graphics.Color.RED
    const val TRACK_STROKE = android.graphics.Color.BLACK
}
