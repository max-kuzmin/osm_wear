package com.osm.wear.view_models

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpxFile
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.services.INavigationTrackingService
import com.osm.wear.services.IMapBoundariesService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GpxTracksViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val gpxRepo: IGpxRepository,
    private val mapBoundariesService: IMapBoundariesService,
    private val navigationTrackingService: INavigationTrackingService
) : ViewModel() {

    private val _isActiveGpxCovered = MutableStateFlow(false)
    val isActiveGpxCovered: StateFlow<Boolean> = _isActiveGpxCovered.asStateFlow()

    val gpxFiles: StateFlow<List<GpxFile>> =
        gpxRepo.files.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeGpxFile: StateFlow<GpxFile?> = gpxRepo.activeGpxFile

    val navigationState = navigationTrackingService.navigationState

    init {
        scanGpxFolders()

        viewModelScope.launch {
            activeGpxFile.collect { active ->
                _isActiveGpxCovered.value = active != null && mapBoundariesService.isGpxCoveredByMap(active)
            }
        }
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

    fun setActiveGpxFile(gpxFile: GpxFile) {
        gpxRepo.setActive(gpxFile.id)
    }

    fun saveCurrentGpx(name: String, points: List<com.osm.wear.models.GpxPoint>, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            gpxRepo.saveGpxFile(name, points)
                .onSuccess { gpx ->
                    setActiveGpxFile(gpx)
                    onResult(true, null)
                }
                .onFailure { error ->
                    onResult(false, error.message)
                }
        }
    }
}
