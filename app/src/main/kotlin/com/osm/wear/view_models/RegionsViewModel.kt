package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.DownloadState
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.MapRegion
import com.osm.wear.repositories.IRegionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val regionRepository: IRegionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegionsUiState())
    val uiState: StateFlow<RegionsUiState> = _uiState.asStateFlow()

    private val _activeRegionId = MutableStateFlow<String?>(null)

    init {
        _activeRegionId.value = regionRepository.getActiveRegionId()
        
        // Group regions once
        val grouped = regionRepository.all.groupBy { it.continent }
        _uiState.update { it.copy(groupedRegions = grouped) }

        viewModelScope.launch {
            regionRepository.activeRegionId.collect { activeId ->
                _activeRegionId.value = activeId
                refreshDownloadedRegionsInternal()
            }
        }

        viewModelScope.launch {
            regionRepository.downloadState.collect { state ->
                _uiState.update { it.copy(downloadState = state) }
                if (state is DownloadState.Idle) {
                    refreshDownloadedRegionsInternal()
                }
            }
        }
        
        refreshDownloadedRegionsInternal()
    }

    fun onIntent(intent: RegionsIntent) {
        when (intent) {
            is RegionsIntent.RefreshDownloadedRegions -> refreshDownloadedRegionsInternal()
            is RegionsIntent.SetActiveRegion -> setActiveRegion(intent.region)
            is RegionsIntent.DeleteRegion -> deleteRegion(intent.region)
            is RegionsIntent.DownloadRegion -> downloadRegion(intent.region)
            is RegionsIntent.CancelDownload -> cancelDownload()
        }
    }

    private fun refreshDownloadedRegionsInternal() {
        val downloaded = regionRepository.getDownloadedRegions(
            activeId = _activeRegionId.value
        )
        _uiState.update { it.copy(downloadedRegions = downloaded) }
    }

    private fun setActiveRegion(region: MapRegion) {
        val file = regionRepository.getLocalFile(region)
        if (!file.exists()) return
        
        _activeRegionId.value = region.id
        regionRepository.setActiveRegionId(region.id)
        refreshDownloadedRegionsInternal()
    }

    private fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            regionRepository.deleteRegion(region)
            if (_activeRegionId.value == region.id) {
                _activeRegionId.value = null
                regionRepository.setActiveRegionId(null)
            }
            refreshDownloadedRegionsInternal()
        }
    }

    private fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            regionRepository.downloadRegion(region)
        }
    }

    private fun cancelDownload() {
        regionRepository.cancelDownload()
    }
}
