package com.osm.wear.data.recording

import android.content.Context
import android.util.Log
import com.osm.wear.domain.model.GpsBatteryMode
import com.osm.wear.domain.model.RecordingSession
import com.osm.wear.domain.model.RecordingState
import com.osm.wear.domain.model.TrackPoint
import com.osm.wear.domain.model.UserLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Records GPS track points with pause/resume capability.
 *
 * Uses HIGH_ACCURACY GPS mode during active recording so every point is
 * captured precisely. When paused the GPS consumer is cancelled entirely
 * (no battery drain while paused).
 *
 * Saves the finished track as a GPX file in [mapsDir].
 */
class TrackRecorder(
    private val context: Context,
    private val mapsDir: File
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _session = MutableStateFlow<RecordingSession?>(null)
    val session: StateFlow<RecordingSession?> = _session.asStateFlow()

    private val points = mutableListOf<TrackPoint>()
    private var locationJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun start(locationFlow: Flow<UserLocation>) {
        if (_session.value?.state == RecordingState.RECORDING) return
        val id = UUID.randomUUID().toString()
        points.clear()
        _session.value = RecordingSession(
            id = id,
            startedAt = System.currentTimeMillis(),
            points = emptyList(),
            state = RecordingState.RECORDING,
            distanceMeters = 0.0
        )
        startCollecting(locationFlow)
        Log.d(TAG, "Recording started id=$id")
    }

    fun pause() {
        val s = _session.value ?: return
        if (s.state != RecordingState.RECORDING) return
        locationJob?.cancel()
        locationJob = null
        _session.value = s.copy(state = RecordingState.PAUSED)
        Log.d(TAG, "Recording paused – ${points.size} pts so far")
    }

    fun resume(locationFlow: Flow<UserLocation>) {
        val s = _session.value ?: return
        if (s.state != RecordingState.PAUSED) return
        _session.value = s.copy(state = RecordingState.RECORDING)
        startCollecting(locationFlow)
        Log.d(TAG, "Recording resumed")
    }

    /**
     * Stops recording, saves the GPX file, and returns the saved [File].
     * Returns null if there are fewer than 2 points.
     */
    fun stop(): File? {
        locationJob?.cancel()
        locationJob = null
        val s = _session.value ?: return null
        _session.value = null

        if (points.size < 2) {
            Log.w(TAG, "Too few points (${points.size}), not saving")
            return null
        }

        val file = saveGpx(s.id, points)
        Log.d(TAG, "Recording saved → ${file.name} (${points.size} pts)")
        return file
    }

    fun cancel() {
        locationJob?.cancel()
        locationJob = null
        _session.value = null
        points.clear()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startCollecting(locationFlow: Flow<UserLocation>) {
        locationJob = scope.launch {
            locationFlow.collect { loc ->
                val pt = TrackPoint(
                    latitude  = loc.latitude,
                    longitude = loc.longitude,
                    elevation = null,
                    timestamp = loc.timestamp
                )
                // Minimum distance filter: skip points closer than 3 m
                val last = points.lastOrNull()
                if (last != null && haversineM(last.latitude, last.longitude, pt.latitude, pt.longitude) < 3.0) {
                    return@collect
                }
                points.add(pt)
                val dist = calculateDistance(points)
                _session.value = _session.value?.copy(
                    points = points.toList(),
                    distanceMeters = dist
                )
            }
        }
    }

    private fun saveGpx(id: String, pts: List<TrackPoint>): File {
        val dir = File(context.filesDir, "recordings").also { it.mkdirs() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val file = File(dir, "track_${dateStr}.gpx")

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="OSM Wear" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("""  <trk><name>Track $dateStr</name><trkseg>""")
        pts.forEach { pt ->
            val elev = if (pt.elevation != null) """<ele>${pt.elevation}</ele>""" else ""
            val time = if (pt.timestamp != null) {
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(pt.timestamp))
                "<time>$iso</time>"
            } else ""
            sb.appendLine("""    <trkpt lat="${pt.latitude}" lon="${pt.longitude}">$elev$time</trkpt>""")
        }
        sb.appendLine("""  </trkseg></trk>""")
        sb.appendLine("""</gpx>""")

        file.writeText(sb.toString())
        return file
    }

    private fun calculateDistance(pts: List<TrackPoint>): Double {
        var total = 0.0
        for (i in 1 until pts.size) {
            total += haversineM(pts[i - 1].latitude, pts[i - 1].longitude,
                                pts[i].latitude, pts[i].longitude)
        }
        return total
    }

    companion object {
        private const val TAG = "TrackRecorder"

        fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
