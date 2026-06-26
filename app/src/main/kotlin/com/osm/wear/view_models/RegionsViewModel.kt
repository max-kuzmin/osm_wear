package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.DownloadState
import com.osm.wear.models.MapRegion
import com.osm.wear.repositories.ISettingsRepository
import com.osm.wear.repositories.MapDownloadRepository
import com.osm.wear.services.IMapRegionCatalogService
import com.osm.wear.repositories.IMapFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val downloadManager: MapDownloadRepository,
    private val mapRegionCatalogService: IMapRegionCatalogService,
    private val settingsRepository: ISettingsRepository,
    private val mapFileRepository: IMapFileRepository
) : ViewModel() {

    private val _downloadedRegions = MutableStateFlow<List<DownloadedRegion>>(emptyList())
    val downloadedRegions: StateFlow<List<DownloadedRegion>> = _downloadedRegions.asStateFlow()

    val groupedRegions: StateFlow<Map<String, List<MapRegion>>> = MutableStateFlow(
        mapRegionCatalogService.all.groupBy { it.continent }
    ).asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    private val _activeMapFile = MutableStateFlow<File?>(null)
    val activeMapFile: StateFlow<File?> = _activeMapFile.asStateFlow()

    private val _activeRegionId = MutableStateFlow<String?>(null)
    val activeRegionId: StateFlow<String?> = _activeRegionId.asStateFlow()

    init {
        refreshDownloadedRegions()
        autoLoadFirstRegion()
    }

    fun refreshDownloadedRegions() {
        _downloadedRegions.value = downloadManager.getDownloadedRegions(
            catalog = mapRegionCatalogService.all,
            activeId = _activeRegionId.value
        )
    }

    fun setActiveRegion(region: MapRegion) {
        val file = downloadManager.getLocalFile(region)
        if (!file.exists()) return
        
        _activeMapFile.value = file
        _activeRegionId.value = region.id
        mapFileRepository.setActiveMapFile(file)
        settingsRepository.setActiveRegionId(region.id)
        refreshDownloadedRegions()
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.deleteRegion(region)
            if (_activeRegionId.value == region.id) {
                _activeMapFile.value = null
                _activeRegionId.value = null
                mapFileRepository.setActiveMapFile(null)
                settingsRepository.setActiveRegionId(null)
            }
            refreshDownloadedRegions()
            autoLoadFirstRegion()
        }
    }

    fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.downloadRegion(region)
            refreshDownloadedRegions()
            if (_activeMapFile.value == null) {
                setActiveRegion(region)
            }
        }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    private fun autoLoadFirstRegion() {
        if (_activeMapFile.value != null) return
        val downloaded = downloadManager.getDownloadedRegions(mapRegionCatalogService.all, null)
        if (downloaded.isEmpty()) return

        val lastSelectedId = settingsRepository.getActiveRegionId()
        val target = downloaded.find { it.region.id == lastSelectedId } ?: downloaded.first()
        
        val file = File(target.filePath)
        if (file.exists()) {
            _activeMapFile.value = file
            _activeRegionId.value = target.region.id
            mapFileRepository.setActiveMapFile(file)
            if (lastSelectedId != target.region.id) {
                settingsRepository.setActiveRegionId(target.region.id)
            }
            refreshDownloadedRegions()
        }
    }
}
