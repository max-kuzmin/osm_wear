package com.osm.wear.services

import android.content.Context
import android.os.Environment
import com.osm.wear.repositories.IRouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ScanTracksFoldersUseCase @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val routeRepo: IRouteRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val foundFiles = mutableListOf<File>()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads.exists()) {
            scanDirectory(downloads, foundFiles)
        }
        foundFiles.forEach { file ->
            routeRepo.importFromFile(file)
        }
        val foundNames = foundFiles.map { it.name }.toSet()
        val internalFiles = File(context.filesDir, "tracks").listFiles()?.filter {
            it.extension.equals("geojson", ignoreCase = true) || it.extension.equals("json", ignoreCase = true)
        } ?: emptyList()
        internalFiles.forEach { internalFile ->
            if (!foundNames.contains(internalFile.name) && internalFile.nameWithoutExtension != "path_finder") {
                routeRepo.deleteRoute(internalFile.name)
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
            } else {
                val ext = file.extension.lowercase()
                if (ext == "geojson" || ext == "json") {
                    result.add(file)
                }
            }
        }
    }
}
