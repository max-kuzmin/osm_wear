package com.osm.wear.services

import android.content.Context
import android.os.Environment
import com.osm.wear.repositories.IGpxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ScanGpxFoldersUseCase @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val gpxRepo: IGpxRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val foundFiles = mutableListOf<File>()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads.exists()) {
            scanDirectory(downloads, foundFiles)
        }
        foundFiles.forEach { file ->
            gpxRepo.importFromFile(file)
        }
        val foundNames = foundFiles.map { it.name }.toSet()
        val internalFiles = File(context.filesDir, "gpx").listFiles()?.filter { it.extension == "gpx" } ?: emptyList()
        internalFiles.forEach { internalFile ->
            if (!foundNames.contains(internalFile.name)) {
                gpxRepo.deleteFile(internalFile.name)
            }
        }
    }

    private fun scanDirectory(dir: File, result: MutableList<File>) {
        if (!dir.exists()) return
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        }
        if (files == null) return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, result)
            } else if (file.extension.lowercase() == "gpx") {
                result.add(file)
            }
        }
    }
}
