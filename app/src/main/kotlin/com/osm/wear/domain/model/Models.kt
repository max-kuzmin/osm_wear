package com.osm.wear.domain.model

// ─── Map Region ──────────────────────────────────────────────────────────────

data class MapRegion(
    val id: String,          // e.g. "europe/germany"
    val name: String,
    val continent: String,
    val downloadUrl: String,
    val fileSizeMb: Int,
    val fileName: String     // e.g. "germany.map"
)

data class DownloadedRegion(
    val region: MapRegion,
    val filePath: String,
    val fileSizeMb: Int,
    val isActive: Boolean
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val region: MapRegion,
        val progressPercent: Int,
        val downloadedMb: Float
    ) : DownloadState()
    data class Failed(val region: MapRegion, val error: String) : DownloadState()
}

// ─── GPX ─────────────────────────────────────────────────────────────────────

data class GpxFile(
    val id: String,
    val name: String,
    val filePath: String,
    val trackPoints: List<GpxPoint>,
    val totalDistanceKm: Double,
    val isActive: Boolean
)

data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double = 0.0,
    val time: Long = 0L
)

// ─── Navigation ──────────────────────────────────────────────────────────────

data class NavigationWaypoint(
    val index: Int,
    val point: GpxPoint,
    val bearingToNext: Float,
    val distanceToNextM: Float,
    val isTurn: Boolean
)

data class NavigationState(
    val isActive: Boolean = false,
    val gpxFile: GpxFile? = null,
    val waypoints: List<NavigationWaypoint> = emptyList(),
    val currentWaypointIndex: Int = 0,
    val distanceToNextTurnM: Float = 0f,
    val bearingToNextTurn: Float = 0f,
    val totalRemainingM: Float = 0f,
    val isOffTrack: Boolean = false,
    val lastAlertedWaypointIndex: Int = -1
)

// ─── GPS ─────────────────────────────────────────────────────────────────────

enum class GpsBatteryMode(
    val intervalMs: Long,
    val minDisplacementM: Float,
    val label: String
) {
    HIGH_ACCURACY(1_000L, 0f, "High Accuracy"),
    BALANCED(5_000L, 5f, "Balanced"),
    LOW_POWER(30_000L, 100f, "Low Power")
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
