package com.osm.wear.presentation.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.osm.wear.data.navigation.NavigationEngine
import com.osm.wear.domain.model.NavigationState
import com.osm.wear.domain.model.TurnDirection
import com.osm.wear.domain.model.UserLocation
import kotlin.math.roundToInt

@Composable
fun NavigationScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit
) {
    val navState by viewModel.navigationState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Vibration alarm: fire once when approaching a turn
    var lastAlarmedWpIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(navState) {
        val state = navState ?: return@LaunchedEffect
        if (state.isFinished) {
            vibratePattern(context, VibrationPattern.ARRIVE)
            return@LaunchedEffect
        }
        val wp = state.waypoints.getOrNull(state.nextWaypointIndex) ?: return@LaunchedEffect
        val isApproaching = state.distanceToNextM <= NavigationEngine.ALARM_DISTANCE_M
        val isTurn = wp.turnDirection != TurnDirection.STRAIGHT &&
                     wp.turnDirection != TurnDirection.START
        if (isApproaching && isTurn && state.nextWaypointIndex != lastAlarmedWpIndex) {
            lastAlarmedWpIndex = state.nextWaypointIndex
            vibratePattern(context, VibrationPattern.TURN)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        when {
            navState == null -> NoNavigationUI(onBack = onBack)
            navState!!.isFinished -> ArrivedUI(onBack = { viewModel.stopNavigation(); onBack() })
            else -> ActiveNavigationUI(
                state = navState!!,
                location = userLocation,
                onStop = { viewModel.stopNavigation(); onBack() }
            )
        }
    }
}

// ── No active navigation ──────────────────────────────────────────────────────

@Composable
private fun NoNavigationUI(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text("No navigation active", color = Color.White,
             style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("Select a GPX track\nand tap Navigate",
             color = Color.Gray, style = MaterialTheme.typography.labelSmall,
             textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        CompactButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Arrived ───────────────────────────────────────────────────────────────────

@Composable
private fun ArrivedUI(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text("🏁", fontSize = 36.sp, textAlign = TextAlign.Center)
        Text("Arrived!", color = Color(0xFF4CAF50),
             style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBack) {
            Text("Done", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Active navigation ─────────────────────────────────────────────────────────

@Composable
private fun ActiveNavigationUI(
    state: NavigationState,
    location: UserLocation?,
    onStop: () -> Unit
) {
    val nextWp = state.waypoints.getOrNull(state.nextWaypointIndex)
    val turnDir = nextWp?.turnDirection ?: TurnDirection.STRAIGHT

    // Compute compass bearing from user to next waypoint
    val bearingToNext = if (location != null && nextWp != null) {
        NavigationEngine.bearing(
            location.latitude, location.longitude,
            nextWp.point.latitude, nextWp.point.longitude
        ).toFloat()
    } else 0f

    // Rotate the arrow: device bearing subtracted so arrow points in real direction
    val deviceBearing = location?.bearing ?: 0f
    val arrowRotation = (bearingToNext - deviceBearing + 360) % 360

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        // Turn icon + direction arrow
        TurnArrow(
            direction = turnDir,
            rotationDeg = arrowRotation,
            modifier = Modifier.size(72.dp)
        )

        // Distance to next turn
        Text(
            text = formatDistanceShort(state.distanceToNextM),
            fontSize = 26.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        // Turn label
        Text(
            text = turnDir.label(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF90CAF9)
        )

        Spacer(Modifier.height(2.dp))

        // Remaining distance
        Text(
            text = "Remaining: ${formatDistanceShort(state.distanceRemainingM)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )

        // Off-track warning
        if (state.offTrackM > NavigationEngine.OFF_TRACK_RADIUS_M) {
            Text(
                text = "⚠ Off track ${state.offTrackM.roundToInt()} m",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF8800)
            )
        }

        Spacer(Modifier.weight(1f))

        CompactButton(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
        ) {
            Text("Stop Nav", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Turn arrow canvas ─────────────────────────────────────────────────────────

@Composable
private fun TurnArrow(
    direction: TurnDirection,
    rotationDeg: Float,
    modifier: Modifier = Modifier
) {
    // Animate rotation smoothly
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDeg,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "arrow_rotation"
    )

    val arrowColor = when (direction) {
        TurnDirection.ARRIVE               -> Color(0xFF4CAF50)
        TurnDirection.U_TURN               -> Color(0xFFFF5722)
        TurnDirection.SHARP_LEFT,
        TurnDirection.SHARP_RIGHT          -> Color(0xFFFF8800)
        TurnDirection.TURN_LEFT,
        TurnDirection.TURN_RIGHT           -> Color(0xFF2196F3)
        else                               -> Color.White
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension / 2f * 0.85f

        rotate(degrees = animatedRotation, pivot = Offset(cx, cy)) {
            drawArrowUp(cx, cy, r, arrowColor)
        }

        // For turns, draw a small curved indicator
        when (direction) {
            TurnDirection.TURN_RIGHT, TurnDirection.SHARP_RIGHT ->
                drawTurnIndicator(cx, cy, r * 0.35f, isRight = true, arrowColor)
            TurnDirection.TURN_LEFT, TurnDirection.SHARP_LEFT ->
                drawTurnIndicator(cx, cy, r * 0.35f, isRight = false, arrowColor)
            TurnDirection.U_TURN ->
                drawUTurnIndicator(cx, cy, r * 0.3f, arrowColor)
            else -> {}
        }
    }
}

private fun DrawScope.drawArrowUp(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx, cy - r)                          // tip
        lineTo(cx + r * 0.4f, cy + r * 0.2f)       // bottom-right
        lineTo(cx + r * 0.15f, cy + r * 0.2f)
        lineTo(cx + r * 0.15f, cy + r * 0.6f)      // shaft right
        lineTo(cx - r * 0.15f, cy + r * 0.6f)      // shaft left
        lineTo(cx - r * 0.15f, cy + r * 0.2f)
        lineTo(cx - r * 0.4f, cy + r * 0.2f)       // bottom-left
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawTurnIndicator(cx: Float, cy: Float, r: Float, isRight: Boolean, color: Color) {
    val sign = if (isRight) 1f else -1f
    val path = Path().apply {
        moveTo(cx + sign * r, cy - r * 0.5f)
        lineTo(cx + sign * r * 1.8f, cy)
        lineTo(cx + sign * r, cy + r * 0.5f)
    }
    drawPath(path, color.copy(alpha = 0.5f))
}

private fun DrawScope.drawUTurnIndicator(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.4f), radius = r, center = Offset(cx, cy))
}

// ── Vibration ─────────────────────────────────────────────────────────────────

private enum class VibrationPattern { TURN, ARRIVE }

private fun vibratePattern(context: Context, pattern: VibrationPattern) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val effect = when (pattern) {
        VibrationPattern.TURN ->
            // Two short pulses: "turn coming"
            VibrationEffect.createWaveform(
                longArrayOf(0, 120, 80, 120), -1
            )
        VibrationPattern.ARRIVE ->
            // Long pulse + short: "arrived"
            VibrationEffect.createWaveform(
                longArrayOf(0, 400, 100, 200), -1
            )
    }
    vibrator.vibrate(effect)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDistanceShort(m: Double) = when {
    m >= 1000 -> "${"%.1f".format(m / 1000)} km"
    else      -> "${m.roundToInt()} m"
}

private fun TurnDirection.label() = when (this) {
    TurnDirection.STRAIGHT    -> "Continue straight"
    TurnDirection.TURN_LEFT   -> "Turn left"
    TurnDirection.TURN_RIGHT  -> "Turn right"
    TurnDirection.SHARP_LEFT  -> "Sharp left"
    TurnDirection.SHARP_RIGHT -> "Sharp right"
    TurnDirection.U_TURN      -> "U-turn"
    TurnDirection.ARRIVE      -> "Destination"
    TurnDirection.START       -> "Start"
}
