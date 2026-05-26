package com.osm.wear.data.gpx

import android.content.Context
import android.net.Uri
import android.util.Log
import com.osm.wear.domain.model.GpxTrack
import com.osm.wear.domain.model.TrackPoint
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import io.ticofab.androidgpxparser.parser.domain.TrackPoint as GpxTrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages GPX file import, parsing, and storage.
 *
 * GPX files are copied to app's private storage: [Context.getFilesDir]/gpx/
 */
class GpxRepository(private val context: Context) {

    private val gpxDir: File get() = File(context.filesDir, "gpx").also { it.mkdirs() }
    private val parser = GPXParser()

    private val _tracks = MutableStateFlow<List<GpxTrack>>(emptyList())
    val tracks: StateFlow<List<GpxTrack>> = _tracks.asStateFlow()

    init {
        // Load existing GPX files on startup
        loadStoredTracks()
    }

    /** Imports a GPX file from a content URI (e.g. from file picker). */
    suspend fun importFromUri(uri: Uri): Result<GpxTrack> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Cannot open URI: $uri"))

            // Determine file name
            val fileName = getFileNameFromUri(uri) ?: "track_${System.currentTimeMillis()}.gpx"
            val destFile = File(gpxDir, fileName)

            // Copy to private storage
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Parse the copied file
            parseGpxFile(destFile)
                .also { result ->
                    if (result.isSuccess) {
                        val updated = _tracks.value.toMutableList()
                        updated.add(result.getOrThrow())
                        _tracks.value = updated
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import GPX from URI: $uri", e)
            Result.failure(e)
        }
    }

    /** Imports a GPX file from a local file path. */
    suspend fun importFromFile(file: File): Result<GpxTrack> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(gpxDir, file.name)
            if (file.canonicalPath != destFile.canonicalPath) {
                file.copyTo(destFile, overwrite = true)
            }
            parseGpxFile(destFile).also { result ->
                if (result.isSuccess) {
                    val updated = _tracks.value.toMutableList()
                    // Replace if already exists
                    val existing = updated.indexOfFirst { it.filePath == destFile.path }
                    if (existing >= 0) updated[existing] = result.getOrThrow()
                    else updated.add(result.getOrThrow())
                    _tracks.value = updated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import GPX file: ${file.path}", e)
            Result.failure(e)
        }
    }

    /** Deletes a GPX track from storage and state. */
    suspend fun deleteTrack(trackId: String) = withContext(Dispatchers.IO) {
        val track = _tracks.value.find { it.id == trackId } ?: return@withContext
        File(track.filePath).delete()
        _tracks.value = _tracks.value.filter { it.id != trackId }
        Log.d(TAG, "Deleted GPX track: ${track.name}")
    }

    /** Toggles the visibility of a GPX track on the map. */
    fun setTrackVisibility(trackId: String, visible: Boolean) {
        _tracks.value = _tracks.value.map { track ->
            if (track.id == trackId) track.copy(isVisible = visible) else track
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun loadStoredTracks() {
        val files = gpxDir.listFiles()?.filter { it.extension == "gpx" } ?: return
        val loaded = files.mapNotNull { file ->
            parseGpxFile(file).getOrNull()
        }
        _tracks.value = loaded
        Log.d(TAG, "Loaded ${loaded.size} GPX tracks from storage")
    }

    private fun parseGpxFile(file: File): Result<GpxTrack> {
        return try {
            val gpx: Gpx? = file.inputStream().use { parser.parse(it) }
                ?: return Result.failure(IOException("Failed to parse GPX: ${file.name}"))

            val allPoints = mutableListOf<TrackPoint>()
            var trackName = file.nameWithoutExtension

            // Extract track points from tracks
            gpx!!.tracks?.forEach { track ->
                if (track.trackName != null) trackName = track.trackName
                track.trackSegments?.forEach { segment ->
                    segment.trackPoints?.forEach { pt ->
                        allPoints.add(pt.toTrackPoint())
                    }
                }
            }

            // Also extract route points if no track points
            if (allPoints.isEmpty()) {
                gpx.routes?.forEach { route ->
                    if (route.routeName != null) trackName = route.routeName
                    route.routePoints?.forEach { pt ->
                        allPoints.add(TrackPoint(pt.latitude, pt.longitude, pt.elevation))
                    }
                }
            }

            if (allPoints.isEmpty()) {
                return Result.failure(IllegalArgumentException("GPX file has no track points: ${file.name}"))
            }

            val distance = calculateDistance(allPoints)

            Result.success(
                GpxTrack(
                    id = UUID.randomUUID().toString(),
                    name = trackName,
                    filePath = file.absolutePath,
                    points = allPoints,
                    distanceMeters = distance,
                    isVisible = true
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "IO error parsing GPX: ${file.name}", e)
            Result.failure(e)
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "XML error parsing GPX: ${file.name}", e)
            Result.failure(e)
        }
    }

    private fun GpxTrackPoint.toTrackPoint() = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        elevation = elevation,
        timestamp = time?.millis
    )

    /** Calculates total track distance in meters using Haversine formula. */
    private fun calculateDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return total
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).let { it * it }
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    companion object {
        private const val TAG = "GpxRepository"
    }
}
