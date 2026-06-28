package com.osm.wear.data_sources

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class LocalFileDataSource(
    private val context: Context
) : ILocalFileDataSource {

    override fun getGpxDirectory(): File {
        return File(context.filesDir, "gpx").also { it.mkdirs() }
    }

    override fun getRegionDirectory(): File {
        return File(context.filesDir, "maps").also { it.mkdirs() }
    }

    override fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    override fun openOutputStream(file: File): OutputStream {
        return file.outputStream()
    }

    override fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && col >= 0) {
                    cursor.getString(col)
                } else null
            }
        } catch (e: Exception) {
            Log.e("LocalFileDataSource", "Error getting filename from URI: $uri", e)
            uri.lastPathSegment
        }
    }
}
