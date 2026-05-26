package com.osm.wear.data.map

import android.content.Context
import android.util.Log
import com.osm.wear.domain.model.DownloadProgress
import com.osm.wear.domain.model.MapRegion
import com.osm.wear.domain.model.RegionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages downloading, storing, and deleting Mapsforge map files.
 *
 * Map files are stored in: [Context.getFilesDir]/maps/<region-id>.map
 */
class MapDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mapsDir: File get() = File(context.filesDir, "maps").also { it.mkdirs() }

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    /** Returns the local .map file for a region, or null if not downloaded. */
    fun getLocalMapFile(region: MapRegion): File? {
        val file = localFileFor(region)
        return if (file.exists() && file.length() > 0) file else null
    }

    /** Returns true if the region map file is already downloaded. */
    fun isDownloaded(region: MapRegion): Boolean = getLocalMapFile(region) != null

    /** Returns all downloaded map files. */
    fun getDownloadedRegionIds(): List<String> {
        return mapsDir.listFiles()
            ?.filter { it.extension == "map" && it.length() > 0 }
            ?.map { it.nameWithoutExtension.replace("_", "/") }
            ?: emptyList()
    }

    /**
     * Downloads a map region file, emitting [DownloadProgress] updates.
     * Supports resuming partial downloads via HTTP Range header.
     */
    fun downloadRegion(region: MapRegion): Flow<DownloadProgress> = flow {
        val destFile = localFileFor(region)
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")

        // Emit initial state
        emit(DownloadProgress(region.id, 0L, region.fileSizeBytes, RegionStatus.DOWNLOADING))
        updateActiveDownload(region.id, 0L, region.fileSizeBytes, RegionStatus.DOWNLOADING)

        try {
            val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
            val requestBuilder = Request.Builder().url(region.downloadUrl)
            if (resumeFrom > 0) {
                requestBuilder.addHeader("Range", "bytes=$resumeFrom-")
                Log.d(TAG, "Resuming download of ${region.name} from byte $resumeFrom")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) resumeFrom + contentLength else region.fileSizeBytes

            body.byteStream().use { input ->
                tempFile.outputStream().let { out ->
                    if (resumeFrom > 0) {
                        // Append to existing partial file
                        tempFile.outputStream().close()
                        java.io.FileOutputStream(tempFile, true)
                    } else {
                        out
                    }
                }.use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = resumeFrom
                    var lastEmit = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        // Emit progress every 512 KB to avoid flooding
                        if (downloaded - lastEmit >= 512 * 1024) {
                            lastEmit = downloaded
                            val progress = DownloadProgress(region.id, downloaded, totalBytes, RegionStatus.DOWNLOADING)
                            emit(progress)
                            updateActiveDownload(region.id, downloaded, totalBytes, RegionStatus.DOWNLOADING)
                        }
                    }
                }
            }

            // Move temp file to final destination
            tempFile.renameTo(destFile)
            Log.d(TAG, "Download complete: ${region.name} -> ${destFile.path}")

            emit(DownloadProgress(region.id, totalBytes, totalBytes, RegionStatus.DOWNLOADED))
            updateActiveDownload(region.id, totalBytes, totalBytes, RegionStatus.DOWNLOADED)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${region.name}", e)
            emit(DownloadProgress(region.id, 0L, region.fileSizeBytes, RegionStatus.ERROR))
            updateActiveDownload(region.id, 0L, region.fileSizeBytes, RegionStatus.ERROR)
        }
    }.flowOn(Dispatchers.IO)

    /** Deletes the downloaded map file for a region. */
    suspend fun deleteRegion(region: MapRegion): Boolean = withContext(Dispatchers.IO) {
        val file = localFileFor(region)
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.delete()
        file.delete()
    }

    /** Returns the total storage used by downloaded maps in bytes. */
    fun totalStorageUsedBytes(): Long =
        mapsDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun localFileFor(region: MapRegion): File {
        val safeName = region.id.replace("/", "_")
        return File(mapsDir, "$safeName.map")
    }

    private fun updateActiveDownload(
        regionId: String, downloaded: Long, total: Long, status: RegionStatus
    ) {
        val current = _activeDownloads.value.toMutableMap()
        if (status == RegionStatus.DOWNLOADED || status == RegionStatus.ERROR) {
            current.remove(regionId)
        } else {
            current[regionId] = DownloadProgress(regionId, downloaded, total, status)
        }
        _activeDownloads.value = current
    }

    companion object {
        private const val TAG = "MapDownloadManager"
    }
}
