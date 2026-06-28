package com.osm.wear.repositories

import com.osm.wear.data_sources.ILocalFileDataSource
import com.osm.wear.data_sources.ILocalPreferencesDataSource
import android.net.Uri
import android.util.Log
import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.TrackPoint as GpxTrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages GPX file import, parsing, and storage.
 * Files are stored in [Context.getFilesDir]/gpx/.
 * Exposes a [StateFlow] of [GpxFile] objects matching the new domain model.
 */
class GpxRepository(
    private val localFileDataSource: ILocalFileDataSource,
    private val prefs: ILocalPreferencesDataSource
) : IGpxRepository {

    private val gpxDir: File get() = localFileDataSource.getGpxDirectory()
    private val parser = GPXParser()

    private val _files = MutableStateFlow<List<GpxFile>>(emptyList())
    override val files: StateFlow<List<GpxFile>> = _files.asStateFlow()

    private val _activeGpxFile = MutableStateFlow<GpxFile?>(null)
    override val activeGpxFile: StateFlow<GpxFile?> = _activeGpxFile.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        loadStoredFiles()
        repositoryScope.launch {
            files.collect { list ->
                _activeGpxFile.value = list.find { it.isActive }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Imports a GPX file from a content URI (file picker). */
    override suspend fun importFromUri(uri: Uri): Result<GpxFile> = withContext(Dispatchers.IO) {
        try {
            val fileName = localFileDataSource.getFileNameFromUri(uri) ?: "track_${System.currentTimeMillis()}.gpx"
            val destFile = File(gpxDir, fileName)
            localFileDataSource.openInputStream(uri)?.use { input ->
                localFileDataSource.openOutputStream(destFile).use { input.copyTo(it) }
            } ?: return@withContext Result.failure(IOException("Cannot open URI: $uri"))
            parseGpxFile(destFile).also { result ->
                if (result.isSuccess) addOrReplace(result.getOrThrow())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import GPX from URI", e)
            Result.failure(e)
        }
    }

    /** Imports a GPX file from a local [File]. */
    override suspend fun importFromFile(file: File): Result<GpxFile> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(gpxDir, file.name)
            if (file.canonicalPath != destFile.canonicalPath) file.copyTo(destFile, overwrite = true)
            parseGpxFile(destFile).also { result ->
                if (result.isSuccess) addOrReplace(result.getOrThrow())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import GPX file", e)
            Result.failure(e)
        }
    }

    /** Deletes a GPX file from storage and state. */
    override suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        val gpx = _files.value.find { it.id == fileId } ?: return@withContext
        File(gpx.filePath).delete()
        if (prefs.getString("active_gpx_id", null) == fileId) {
            prefs.remove("active_gpx_id")
        }
        _files.value = _files.value.filter { it.id != fileId }
    }

    /** Sets the active GPX file (only one can be active at a time). */
    override fun setActive(fileId: String) {
        prefs.putString("active_gpx_id", fileId)
        _files.value = _files.value.map { it.copy(isActive = it.id == fileId) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun addOrReplace(gpx: GpxFile) {
        val current = _files.value.toMutableList()
        val idx = current.indexOfFirst { it.filePath == gpx.filePath }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = gpx.copy(id = existing.id, isActive = existing.isActive)
        } else {
            val activeId = prefs.getString("active_gpx_id", null)
            current.add(gpx.copy(isActive = gpx.id == activeId))
        }
        _files.value = current
    }

    private fun loadStoredFiles() {
        val activeId = prefs.getString("active_gpx_id", null)
        val loaded = gpxDir.listFiles()
            ?.filter { it.extension == "gpx" }
            ?.mapNotNull { parseGpxFile(it).getOrNull() }
            ?.map { it.copy(isActive = it.id == activeId) }
            ?: emptyList()
        _files.value = loaded
        Log.d(TAG, "Loaded ${loaded.size} GPX files from storage")
    }

    private fun parseGpxFile(file: File): Result<GpxFile> {
        return try {
            val gpx = file.inputStream().use { parser.parse(it) }
                ?: return Result.failure(IOException("Failed to parse: ${file.name}"))

            val points = mutableListOf<GpxPoint>()
            var name = file.nameWithoutExtension

            gpx.tracks?.forEach { track ->
                if (!track.trackName.isNullOrBlank()) name = track.trackName
                track.trackSegments?.forEach { seg ->
                    seg.trackPoints?.forEach { pt -> points.add(pt.toGpxPoint()) }
                }
            }

            if (points.isEmpty()) {
                gpx.routes?.forEach { route ->
                    if (!route.routeName.isNullOrBlank()) name = route.routeName
                    route.routePoints?.forEach { pt ->
                        points.add(GpxPoint(pt.latitude, pt.longitude, pt.elevation ?: 0.0))
                    }
                }
            }

            if (points.isEmpty())
                return Result.failure(IllegalArgumentException("No track points in ${file.name}"))

            val distKm = calculateDistanceKm(points)

            Result.success(
                GpxFile(
                    id = file.name,
                    name = name,
                    filePath = file.absolutePath,
                    trackPoints = points,
                    totalDistanceKm = distKm,
                    isActive = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GPX: ${file.name}", e)
            Result.failure(e)
        }
    }

    private fun GpxTrackPoint.toGpxPoint() = GpxPoint(
        lat = latitude,
        lon = longitude,
        ele = elevation ?: 0.0,
        time = time?.millis ?: 0L
    )

    private fun calculateDistanceKm(points: List<GpxPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineM(points[i - 1], points[i])
        }
        return total / 1000.0
    }

    private fun haversineM(a: GpxPoint, b: GpxPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
                sin(dLon / 2).let { it * it }
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * atan2(sqrt(clampedH), sqrt(1.0 - clampedH))
    }

    // Removed getFileNameFromUri, handled by localFileDataSource

    override suspend fun saveGpxFile(name: String, points: List<GpxPoint>): Result<GpxFile> = withContext(Dispatchers.IO) {
        try {
            val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${safeName}_${System.currentTimeMillis()}.gpx"
            val destFile = File(gpxDir, fileName)
            val xml = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
                append("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"osm_wear\">\n")
                append("  <trk>\n")
                append("    <name>").append(name).append("</name>\n")
                append("    <trkseg>\n")
                for (pt in points) {
                    append("      <trkpt lat=\"").append(pt.lat).append("\" lon=\"").append(pt.lon).append("\">\n")
                    if (pt.ele != 0.0) {
                        append("        <ele>").append(pt.ele).append("</ele>\n")
                    }
                    append("      </trkpt>\n")
                }
                append("    </trkseg>\n")
                append("  </trk>\n")
                append("</gpx>\n")
            }
            destFile.writeText(xml)
            parseGpxFile(destFile).also { result ->
                if (result.isSuccess) {
                    addOrReplace(result.getOrThrow())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save GPX file", e)
            Result.failure(e)
        }
    }

    companion object { private const val TAG = "GpxRepository" }
}

