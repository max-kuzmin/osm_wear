package com.osm.wear.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// Map regions
// ─────────────────────────────────────────────────────────────────────────────

data class MapRegion(
    val id: String,
    val name: String,
    val continent: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val status: RegionStatus = RegionStatus.NOT_DOWNLOADED
)

enum class RegionStatus {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, ERROR
}

data class DownloadProgress(
    val regionId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: RegionStatus
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
}

// ─────────────────────────────────────────────────────────────────────────────
// GPX tracks (imported)
// ─────────────────────────────────────────────────────────────────────────────

data class GpxTrack(
    val id: String,
    val name: String,
    val filePath: String,
    val points: List<TrackPoint>,
    val distanceMeters: Double,
    val isVisible: Boolean = true
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timestamp: Long? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// GPS location
// ─────────────────────────────────────────────────────────────────────────────

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float? = null,
    val speed: Float? = null,          // m/s
    val timestamp: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// GPS battery mode
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Controls the trade-off between GPS accuracy and battery consumption.
 *
 * POWER_SAVE  – 10 s interval, 20 m displacement filter → minimal drain
 * BALANCED    – 5 s interval,  5 m displacement filter  → default
 * HIGH        – 1 s interval,  0 m displacement filter  → navigation / recording
 */
enum class GpsBatteryMode(
    val intervalMs: Long,
    val minDisplacementM: Float,
    val label: String
) {
    POWER_SAVE(10_000L, 20f, "Power Save"),
    BALANCED(5_000L, 5f, "Balanced"),
    HIGH_ACCURACY(1_000L, 0f, "High Accuracy")
}

// ─────────────────────────────────────────────────────────────────────────────
// GPX track recording
// ─────────────────────────────────────────────────────────────────────────────

enum class RecordingState { IDLE, RECORDING, PAUSED }

data class RecordingSession(
    val id: String,
    val startedAt: Long,
    val points: List<TrackPoint>,
    val state: RecordingState,
    val distanceMeters: Double
)

// ─────────────────────────────────────────────────────────────────────────────
// Turn-by-turn navigation
// ─────────────────────────────────────────────────────────────────────────────

enum class TurnDirection {
    STRAIGHT, TURN_LEFT, TURN_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    U_TURN, ARRIVE, START
}

data class NavigationWaypoint(
    val index: Int,
    val point: TrackPoint,
    /** Bearing from previous point to this one (0–360°, 0 = North). */
    val bearingIn: Double,
    /** Bearing from this point to next point. */
    val bearingOut: Double,
    val turnDirection: TurnDirection,
    /** Cumulative distance from track start to this waypoint (metres). */
    val distanceFromStart: Double,
    /** Distance from this waypoint to the next one (metres). */
    val distanceToNext: Double
)

data class NavigationState(
    val track: GpxTrack,
    val waypoints: List<NavigationWaypoint>,
    /** Index into [waypoints] of the next upcoming turn. */
    val nextWaypointIndex: Int,
    val distanceToNextM: Double,
    val distanceRemainingM: Double,
    val offTrackM: Double,
    val isFinished: Boolean = false
)
