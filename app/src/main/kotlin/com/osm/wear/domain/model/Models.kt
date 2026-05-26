package com.osm.wear.domain.model

/**
 * Represents an offline map region available for download.
 */
data class MapRegion(
    val id: String,
    val name: String,
    val continent: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val status: RegionStatus = RegionStatus.NOT_DOWNLOADED
)

enum class RegionStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

/**
 * Download progress for a map region.
 */
data class DownloadProgress(
    val regionId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: RegionStatus
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
}

/**
 * A parsed GPX track with metadata.
 */
data class GpxTrack(
    val id: String,
    val name: String,
    val filePath: String,
    val points: List<TrackPoint>,
    val distanceMeters: Double,
    val isVisible: Boolean = true
)

/**
 * A single GPS coordinate point in a GPX track.
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timestamp: Long? = null
)

/**
 * Current GPS location of the user.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
