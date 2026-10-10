package com.osm.wear.view_models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import com.osm.wear.repositories.IRouteRepository
import com.osm.wear.services.ScanTracksFoldersUseCase
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
class TracksViewModel @Inject constructor(
    private val routeRepo: IRouteRepository,
    private val scanTracksFoldersUseCase: ScanTracksFoldersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TracksUiState())
    val uiState: StateFlow<TracksUiState> = _uiState.asStateFlow()

    private val _effect = Channel<TracksEffect>(Channel.BUFFERED)
    val effect: Flow<TracksEffect> = _effect.receiveAsFlow()

    init {
        observeRepositories()
    }

    private fun observeRepositories() {
        viewModelScope.launch {
            combine(
                routeRepo.routes,
                routeRepo.activeRoute
            ) { routes, activeRoute ->
                routes to activeRoute
            }.collect { (routes, activeRoute) ->
                _uiState.update {
                    it.copy(
                        routes = routes,
                        activeRoute = activeRoute
                    )
                }
            }
        }
    }

    fun onIntent(intent: TracksIntent) {
        when (intent) {
            is TracksIntent.ScanFolders -> scanFolders()
            is TracksIntent.SetActive -> setActiveRoute(intent.routeId)
            is TracksIntent.SaveCurrent -> saveCurrentRoute(intent.name, intent.route)
            is TracksIntent.ImportTrack -> importTrack(intent.uri)
            is TracksIntent.DeleteTrack -> deleteRoute(intent.routeId)
        }
    }

    private fun scanFolders() {
        viewModelScope.launch {
            scanTracksFoldersUseCase()
        }
    }

    private fun setActiveRoute(routeId: String) {
        routeRepo.setActive(routeId)
    }

    private fun importTrack(uri: Uri) {
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            routeRepo.importFromUri(uri)
                .onSuccess { routeFile ->
                    routeRepo.setActive(routeFile.id)
                    _uiState.update { it.copy(isImporting = false) }
                    _effect.send(TracksEffect.ShowToast("Track imported: ${routeFile.name}"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isImporting = false) }
                    _effect.send(TracksEffect.ShowToast("Import failed: ${error.message ?: "Invalid file"}"))
                }
        }
    }

    private fun saveCurrentRoute(name: String, route: NavigationRoute) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            routeRepo.saveRoute(name, route)
                .onSuccess { savedRoute ->
                    routeRepo.setActive(savedRoute.id)
                    _uiState.update { it.copy(isSaving = false) }
                    _effect.send(TracksEffect.ShowToast("Track saved successfully"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _effect.send(TracksEffect.ShowToast("Save failed: ${error.message ?: "Error"}"))
                }
        }
    }

    private fun deleteRoute(routeId: String) {
        viewModelScope.launch {
            routeRepo.deleteRoute(routeId)
            _effect.send(TracksEffect.ShowToast("Track deleted"))
        }
    }

    override fun onCleared() {
        super.onCleared()
        _effect.close()
    }
}
