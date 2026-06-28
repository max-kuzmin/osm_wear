package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.DownloadState
import com.osm.wear.models.MapRegion
import com.osm.wear.repositories.IRegionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val regionRepository: IRegionRepository
) : ViewModel() {

    private val _downloadedRegions = MutableStateFlow<List<DownloadedRegion>>(emptyList())
    val downloadedRegions: StateFlow<List<DownloadedRegion>> = _downloadedRegions.asStateFlow()

    val groupedRegions: StateFlow<Map<String, List<MapRegion>>> = MutableStateFlow(
        regionRepository.all.groupBy { it.continent }
    ).asStateFlow()

    val downloadState: StateFlow<DownloadState> = regionRepository.downloadState

    private val _activeRegionId = MutableStateFlow<String?>(null)

    init {
        _activeRegionId.value = regionRepository.getActiveRegionId()
        refreshDownloadedRegions()
        
        viewModelScope.launch {
            regionRepository.activeRegionId.collect { activeId ->
                _activeRegionId.value = activeId
                refreshDownloadedRegions()
            }
        }

        viewModelScope.launch {
            regionRepository.downloadState.collect { state ->
                if (state is DownloadState.Idle) {
                    refreshDownloadedRegions()
                }
            }
        }
    }

    fun refreshDownloadedRegions() {
        _downloadedRegions.value = regionRepository.getDownloadedRegions(
            activeId = _activeRegionId.value
        )
    }

    fun setActiveRegion(region: MapRegion) {
        val file = regionRepository.getLocalFile(region)
        if (!file.exists()) return
        
        _activeRegionId.value = region.id
        regionRepository.setActiveRegionId(region.id)
        refreshDownloadedRegions()
    }

    fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            regionRepository.deleteRegion(region)
            if (_activeRegionId.value == region.id) {
                _activeRegionId.value = null
                regionRepository.setActiveRegionId(null)
            }
            refreshDownloadedRegions()
        }
    }

    fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            regionRepository.downloadRegion(region)
        }
    }

    fun cancelDownload() {
        regionRepository.cancelDownload()
    }
}
