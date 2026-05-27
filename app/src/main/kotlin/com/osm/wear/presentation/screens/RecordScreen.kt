package com.osm.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.osm.wear.domain.model.RecordingState
import kotlin.math.roundToInt

@Composable
fun RecordScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.recordingSession.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        when {
            session == null -> IdleRecordUI(
                onStart = { viewModel.startRecording() },
                onBack = onBack
            )
            session!!.state == RecordingState.RECORDING -> ActiveRecordUI(
                distanceM = session!!.distanceMeters,
                pointCount = session!!.points.size,
                elapsedMs = System.currentTimeMillis() - session!!.startedAt,
                onPause = { viewModel.pauseRecording() },
                onStop = { viewModel.stopRecording(); onBack() }
            )
            session!!.state == RecordingState.PAUSED -> PausedRecordUI(
                distanceM = session!!.distanceMeters,
                pointCount = session!!.points.size,
                onResume = { viewModel.resumeRecording() },
                onStop = { viewModel.stopRecording(); onBack() },
                onCancel = { viewModel.cancelRecording(); onBack() }
            )
            else -> {}
        }
    }
}

// ── Idle (not recording yet) ──────────────────────────────────────────────────

@Composable
private fun IdleRecordUI(onStart: () -> Unit, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            "Record Track",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            "GPS will switch to\nHigh Accuracy mode",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onStart,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000))
        ) {
            Text("●", fontSize = 22.sp, color = Color.White)
        }
        CompactButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Actively recording ────────────────────────────────────────────────────────

@Composable
private fun ActiveRecordUI(
    distanceM: Double,
    pointCount: Int,
    elapsedMs: Long,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    // Refresh elapsed time every second
    var elapsed by remember { mutableLongStateOf(elapsedMs) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            elapsed += 1_000
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(12.dp)
    ) {
        // Blinking REC indicator
        var blink by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(700)
                blink = !blink
            }
        }
        Text(
            text = if (blink) "● REC" else "  REC",
            color = Color(0xFFFF3333),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = formatElapsed(elapsed),
            fontSize = 28.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = formatDistance(distanceM),
            fontSize = 18.sp,
            color = Color(0xFF4FC3F7)
        )

        Text(
            text = "$pointCount pts",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Pause
            CompactButton(
                onClick = onPause,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8800))
            ) {
                Text("⏸", fontSize = 16.sp)
            }
            // Stop & save
            CompactButton(
                onClick = onStop,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
            ) {
                Text("⏹", fontSize = 16.sp)
            }
        }
    }
}

// ── Paused ────────────────────────────────────────────────────────────────────

@Composable
private fun PausedRecordUI(
    distanceM: Double,
    pointCount: Int,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(12.dp)
    ) {
        Text(
            "⏸ PAUSED",
            color = Color(0xFFFF8800),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatDistance(distanceM),
            fontSize = 22.sp,
            color = Color(0xFF4FC3F7)
        )
        Text(
            text = "$pointCount pts recorded",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Resume
            CompactButton(
                onClick = onResume,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA44))
            ) {
                Text("▶", fontSize = 16.sp)
            }
            // Stop & save
            CompactButton(
                onClick = onStop,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("💾", fontSize = 14.sp)
            }
            // Cancel (discard)
            CompactButton(
                onClick = onCancel,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
            ) {
                Text("✕", fontSize = 14.sp)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun formatDistance(m: Double) = when {
    m >= 1000 -> "${"%.2f".format(m / 1000)} km"
    else      -> "${m.roundToInt()} m"
}
