package com.osm.wear.data.map

import android.content.Context
import android.util.Log
import com.osm.wear.domain.model.DownloadState
import com.osm.wear.domain.model.DownloadedRegion
import com.osm.wear.domain.model.MapRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads, stores, and manages Mapsforge .map files.
 * Files are stored in [Context.getFilesDir]/maps/.
 */
class MapDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mapsDir: File get() = File(context.filesDir, "maps").also { it.mkdirs() }

    // ── Download state ────────────────────────────────────────────────────────

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // ── Query helpers ─────────────────────────────────────────────────────────

    fun getLocalFile(region: MapRegion): File = File(mapsDir, safeFileName(region))

    fun isDownloaded(region: MapRegion): Boolean =
        getLocalFile(region).let { it.exists() && it.length() > 0 }

    /** Returns all downloaded regions as [DownloadedRegion] objects. */
    fun getDownloadedRegions(catalog: List<MapRegion>, activeId: String?): List<DownloadedRegion> {
        return mapsDir.listFiles()
            ?.filter { it.extension == "map" && it.length() > 0 }
            ?.mapNotNull { file ->
                val regionId = file.nameWithoutExtension.replace("_", "/")
                val region = catalog.find { it.id == regionId }
                    ?: MapRegion(regionId, regionId, "Other", "", (file.length() / 1_048_576).toInt(), file.name)
                DownloadedRegion(
                    region = region,
                    filePath = file.absolutePath,
                    fileSizeMb = (file.length() / 1_048_576).toInt(),
                    isActive = region.id == activeId
                )
            }
            ?: emptyList()
    }

    /** Total storage used by downloaded maps in bytes. */
    fun totalStorageBytes(): Long = mapsDir.listFiles()?.sumOf { it.length() } ?: 0L

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Downloads a map region file, updating [downloadState] with progress.
     * This is a suspend function that blocks until the download completes or fails.
     * Supports resuming partial downloads via HTTP Range header.
     */
    suspend fun downloadRegion(region: MapRegion) = withContext(Dispatchers.IO) {
        val destFile = getLocalFile(region)
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")

        _downloadState.value = DownloadState.Downloading(region, 0, 0f)

        try {
            val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
            val requestBuilder = Request.Builder().url(region.downloadUrl)
            if (resumeFrom > 0) {
                requestBuilder.addHeader("Range", "bytes=$resumeFrom-")
                Log.d(TAG, "Resuming ${region.name} from byte $resumeFrom")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) resumeFrom + contentLength
            else region.fileSizeMb.toLong() * 1_048_576

            body.byteStream().use { input ->
                val output = if (resumeFrom > 0) java.io.FileOutputStream(tempFile, true)
                else tempFile.outputStream()
                output.use { out ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = resumeFrom
                    var lastEmit = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (downloaded - lastEmit >= 256 * 1024) {
                            lastEmit = downloaded
                            val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                            val mb = downloaded / 1_048_576f
                            _downloadState.value = DownloadState.Downloading(region, pct, mb)
                        }
                    }
                }
            }

            tempFile.renameTo(destFile)
            Log.d(TAG, "Download complete: ${region.name}")
            _downloadState.value = DownloadState.Idle

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${region.name}", e)
            _downloadState.value = DownloadState.Failed(region, e.message ?: "Unknown error")
        }
    }

    /** Deletes the downloaded map file for a region. */
    suspend fun deleteRegion(region: MapRegion) = withContext(Dispatchers.IO) {
        getLocalFile(region).delete()
        File(mapsDir, "${safeFileName(region)}.tmp").delete()
        Log.d(TAG, "Deleted region: ${region.name}")
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun safeFileName(region: MapRegion): String =
        region.id.replace("/", "_") + ".map"

    companion object { private const val TAG = "MapDownloadManager" }
}
