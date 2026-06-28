package com.osm.wear.repositories

import android.net.Uri
import com.osm.wear.models.GpxFile
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface IGpxRepository {
    val files: StateFlow<List<GpxFile>>
    suspend fun importFromUri(uri: Uri): Result<GpxFile>
    suspend fun importFromFile(file: File): Result<GpxFile>
    suspend fun deleteFile(fileId: String)
    fun setActive(fileId: String)
    fun clearActive()
    suspend fun saveGpxFile(name: String, points: List<com.osm.wear.models.GpxPoint>): Result<GpxFile>
}

