package com.osm.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.osm.wear.models.NavigationState
import com.osm.wear.presentation.theme.AppDimensions
import com.osm.wear.presentation.theme.MapUiAlpha

/**
 * Decoupled Turn-by-Turn Navigation Overlay for Wear OS.
 * Displays upcoming maneuver distance, direction arrow, target road name, and off-track warning.
 */
@Composable
fun NavigationOverlay(
    navState: NavigationState,
    modifier: Modifier = Modifier
) {
    val distM = navState.distanceToNextTurnM
    val distText = if (distM >= 1000f) "%.1f km".format(distM / 1000f) else "${distM.toInt()} m"
    val targetRoad = navState.activeManeuver?.roadName ?: navState.currentRoadName

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MapUiAlpha),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(
                horizontal = AppDimensions.NavOverlayPaddingHorizontal,
                vertical = AppDimensions.NavOverlayPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.NavOverlaySpacing)
    ) {
        Icon(
            imageVector = if (navState.hasNextTurn) Icons.Default.ArrowUpward else Icons.Default.LocationOn,
            contentDescription = if (navState.hasNextTurn) "Turn direction" else "Destination",
            modifier = Modifier
                .size(AppDimensions.NavOverlayIconSize)
                .rotate(if (navState.hasNextTurn) navState.bearingToNextTurn else 0f),
            tint = MaterialTheme.colorScheme.onSurface
        )

        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = distText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!targetRoad.isNullOrBlank()) {
                Text(
                    text = targetRoad,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
        }

        if (navState.isOffTrack) {
            Text(
                text = "⚠",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
