package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.DownloadState
import com.osm.wear.models.MapRegion
import com.osm.wear.repositories.IRegionCatalogRepository
import com.osm.wear.repositories.IRegionRepository
import com.osm.wear.services.IMapDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val downloadManager: IMapDownloadService,
    private val regionCatalogRepository: IRegionCatalogRepository,
    private val regionRepository: IRegionRepository
) : ViewModel() {

    private val _downloadedRegions = MutableStateFlow<List<DownloadedRegion>>(emptyList())
    val downloadedRegions: StateFlow<List<DownloadedRegion>> = _downloadedRegions.asStateFlow()

    val groupedRegions: StateFlow<Map<String, List<MapRegion>>> = MutableStateFlow(
        regionCatalogRepository.all.groupBy { it.continent }
    ).asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    private val _activeRegionId = MutableStateFlow<String?>(null)

    init {
        refreshDownloadedRegions()
        autoLoadFirstRegion()
    }

    fun refreshDownloadedRegions() {
        _downloadedRegions.value = downloadManager.getDownloadedRegions(
            catalog = regionCatalogRepository.all,
            activeId = _activeRegionId.value
        )
    }

    fun setActiveRegion(region: MapRegion) {
        val file = downloadManager.getLocalFile(region)
        if (!file.exists()) return
        
        _activeRegionId.value = region.id
        regionRepository.setActiveRegionId(region.id)
        refreshDownloadedRegions()
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.deleteRegion(region)
            if (_activeRegionId.value == region.id) {
                _activeRegionId.value = null
                regionRepository.setActiveRegionId(null)
            }
            refreshDownloadedRegions()
            autoLoadFirstRegion()
        }
    }

    fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            downloadManager.downloadRegion(region)
            refreshDownloadedRegions()
            if (regionRepository.getActiveMapFile() == null) {
                setActiveRegion(region)
            }
        }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    private fun autoLoadFirstRegion() {
        if (regionRepository.getActiveMapFile() != null) return
        val downloaded = downloadManager.getDownloadedRegions(regionCatalogRepository.all, null)
        if (downloaded.isEmpty()) return

        val lastSelectedId = regionRepository.getActiveRegionId()
        val target = downloaded.find { it.region.id == lastSelectedId } ?: downloaded.first()
        
        val file = File(target.filePath)
        if (file.exists()) {
            _activeRegionId.value = target.region.id
            if (lastSelectedId != target.region.id) {
                regionRepository.setActiveRegionId(target.region.id)
            }
            refreshDownloadedRegions()
        }
    }
}
