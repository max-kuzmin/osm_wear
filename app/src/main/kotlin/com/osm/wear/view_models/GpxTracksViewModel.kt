package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.services.CheckGpxCoverageUseCase
import com.osm.wear.services.ScanGpxFoldersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GpxTracksViewModel @Inject constructor(
    private val gpxRepo: IGpxRepository,
    private val checkGpxCoverageUseCase: CheckGpxCoverageUseCase,
    private val scanGpxFoldersUseCase: ScanGpxFoldersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GpxUiState())
    val uiState: StateFlow<GpxUiState> = _uiState.asStateFlow()

    private val _effect = Channel<GpxEffect>(Channel.BUFFERED)
    val effect: Flow<GpxEffect> = _effect.receiveAsFlow()

    init {
        observeRepositories()
    }

    private fun observeRepositories() {
        viewModelScope.launch {
            combine(
                gpxRepo.files,
                gpxRepo.activeGpxFile
            ) { files, activeFile ->
                val isCovered = activeFile != null && checkGpxCoverageUseCase(activeFile)
                Triple(files, activeFile, isCovered)
            }.collect { (files, activeFile, isCovered) ->
                _uiState.update {
                    it.copy(
                        gpxFiles = files,
                        activeGpxFile = activeFile,
                        isActiveGpxCovered = isCovered
                    )
                }
            }
        }
    }

    fun onIntent(intent: GpxIntent) {
        when (intent) {
            is GpxIntent.ScanFolders -> scanGpxFolders()
            is GpxIntent.SetActive -> setActiveGpxFile(intent.gpxFile)
            is GpxIntent.SaveCurrent -> saveCurrentGpx(intent.name, intent.points)
        }
    }

    private fun scanGpxFolders() {
        viewModelScope.launch {
            scanGpxFoldersUseCase()
        }
    }

    private fun setActiveGpxFile(gpxFile: GpxFile) {
        gpxRepo.setActive(gpxFile.id)
    }

    private fun saveCurrentGpx(name: String, points: List<GpxPoint>) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            gpxRepo.saveGpxFile(name, points)
                .onSuccess { gpx ->
                    setActiveGpxFile(gpx)
                    _uiState.update { it.copy(isSaving = false) }
                    _effect.send(GpxEffect.ShowToast("Track saved successfully"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _effect.send(GpxEffect.ShowToast("Save failed: ${error.message}"))
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _effect.close()
    }
}
