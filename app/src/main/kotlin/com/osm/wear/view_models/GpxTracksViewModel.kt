package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpxFile
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.services.INavigationTrackingService
import com.osm.wear.services.CheckGpxCoverageUseCase
import com.osm.wear.services.ScanGpxFoldersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GpxTracksViewModel @Inject constructor(
    private val gpxRepo: IGpxRepository,
    private val checkGpxCoverageUseCase: CheckGpxCoverageUseCase,
    private val scanGpxFoldersUseCase: ScanGpxFoldersUseCase
) : ViewModel() {

    private val _isActiveGpxCovered = MutableStateFlow(false)
    val isActiveGpxCovered: StateFlow<Boolean> = _isActiveGpxCovered.asStateFlow()

    val gpxFiles: StateFlow<List<GpxFile>> =
        gpxRepo.files.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeGpxFile: StateFlow<GpxFile?> = gpxRepo.activeGpxFile

    init {
        scanGpxFolders()

        viewModelScope.launch {
            activeGpxFile.collect { active ->
                _isActiveGpxCovered.value = active != null && checkGpxCoverageUseCase(active)
            }
        }
    }

    fun scanGpxFolders() {
        viewModelScope.launch {
            scanGpxFoldersUseCase()
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
