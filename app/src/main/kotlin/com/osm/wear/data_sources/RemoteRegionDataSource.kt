package com.osm.wear.data_sources

import android.util.Log
import com.osm.wear.models.MapRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class RemoteRegionDataSource(
    private val client: OkHttpClient
) : IRemoteRegionDataSource {

    private var currentCall: Call? = null

    override suspend fun downloadRegion(
        region: MapRegion,
        tempFile: File,
        onProgress: (Int, Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        val requestBuilder = Request.Builder().url(region.downloadUrl)
        if (resumeFrom > 0) {
            requestBuilder.addHeader("Range", "bytes=$resumeFrom-")
            Log.d("RemoteRegionDataSource", "Resuming ${region.name} from byte $resumeFrom")
        }

        val call = client.newCall(requestBuilder.build())
        currentCall = call
        val response = call.execute()
        if (!response.isSuccessful && response.code != 206) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) resumeFrom + contentLength
        else region.fileSizeMb.toLong() * 1_048_576

        body.byteStream().use { input ->
            val output = if (resumeFrom > 0) FileOutputStream(tempFile, true)
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
                        onProgress(pct, mb)
                    }
                }
            }
        }
        currentCall = null
    }

    override fun cancelDownload() {
        currentCall?.cancel()
    }
}
