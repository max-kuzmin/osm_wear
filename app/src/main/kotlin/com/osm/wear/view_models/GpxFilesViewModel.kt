package com.osm.wear.view_models

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpxFile
import com.osm.wear.repositories.IGpxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GpxFilesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val gpxRepo: IGpxRepository
) : ViewModel() {

    val gpxFiles: StateFlow<List<GpxFile>> =
        gpxRepo.files.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeGpxFile = MutableStateFlow<GpxFile?>(null)
    val activeGpxFile: StateFlow<GpxFile?> = _activeGpxFile.asStateFlow()

    init {
        autoLoadActiveGpx()
        scanGpxFolders()
    }

    fun importGpxFile(uri: Uri, autoActivate: Boolean = false) {
        viewModelScope.launch {
            gpxRepo.importFromUri(uri).onSuccess { gpx ->
                if (autoActivate) {
                    setActiveGpxFile(gpx)
                }
            }
        }
    }

    fun importGpxFromFile(file: File) {
        viewModelScope.launch { gpxRepo.importFromFile(file) }
    }

    fun scanGpxFolders() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
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

    fun deleteGpxFile(fileId: String) {
        viewModelScope.launch {
            if (_activeGpxFile.value?.id == fileId) {
                _activeGpxFile.value = null
            }
            gpxRepo.deleteFile(fileId)
        }
    }

    fun setActiveGpxFile(gpxFile: GpxFile) {
        gpxRepo.setActive(gpxFile.id)
        _activeGpxFile.value = gpxFile
    }

    fun clearActiveGpxFile() {
        gpxRepo.clearActive()
        _activeGpxFile.value = null
    }

    private fun autoLoadActiveGpx() {
        viewModelScope.launch {
            gpxFiles.collect { files ->
                val active = files.find { it.isActive }
                _activeGpxFile.value = active
            }
        }
    }
}
